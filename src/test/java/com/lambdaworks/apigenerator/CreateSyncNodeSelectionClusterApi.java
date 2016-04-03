package com.lambdaworks.apigenerator;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.lambdaworks.redis.internal.LettuceSets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.lambdaworks.redis.internal.LettuceLists;

/**
 * Create sync API based on the templates.
 *
 * @author Mark Paluch
 */
@RunWith(Parameterized.class)
public class CreateSyncNodeSelectionClusterApi {

    private Set<String> FILTER_METHODS = LettuceSets.unmodifiableSet("shutdown", "debugOom", "debugSegfault",
            "digest", "close", "isOpen", "BaseRedisCommands.reset", "readOnly", "readWrite");

    private CompilationUnitFactory factory;

    @Parameterized.Parameters(name = "Create {0}")
    public static List<Object[]> arguments() {
        List<Object[]> result = LettuceLists.newList();

        for (String templateName : Constants.TEMPLATE_NAMES) {
            if (templateName.contains("Transactional") || templateName.contains("Sentinel")) {
                continue;
            }
            result.add(new Object[] { templateName });
        }

        return result;
    }

    /**
     * @param templateName
     */
    public CreateSyncNodeSelectionClusterApi(String templateName) {

        String targetName = templateName.replace("Redis", "NodeSelection");
        File templateFile = new File(Constants.TEMPLATES, "com/lambdaworks/redis/api/" + templateName + ".java");
        String targetPackage = "com.lambdaworks.redis.cluster.api.sync";

        // todo: remove AutoCloseable from BaseNodeSelectionAsyncCommands
        factory = new CompilationUnitFactory(templateFile, Constants.SOURCES, targetPackage, targetName, commentMutator(),
                methodTypeMutator(), methodFilter(), importSupplier(), null, null);
    }

    /**
     * Mutate type comment.
     *
     * @return
     */
    protected Function<String, String> commentMutator() {
        return s -> s.replaceAll("\\$\\{intent\\}", "Synchronous executed commands on a node selection") + "* @generated by "
                + getClass().getName() + "\r\n ";
    }

    /**
     * Mutate type to async result.
     *
     * @return
     */
    protected Predicate<MethodDeclaration> methodFilter() {
        return method -> {
            ClassOrInterfaceDeclaration classOfMethod = (ClassOrInterfaceDeclaration) method.getParentNode();
            if (FILTER_METHODS.contains(method.getName())
                    || FILTER_METHODS.contains(classOfMethod.getName() + "." + method.getName())) {
                return false;
            }

            return true;
        };
    }

    /**
     * Mutate type to async result.
     *
     * @return
     */
    protected Function<MethodDeclaration, Type> methodTypeMutator() {
        return method -> {
            ClassOrInterfaceDeclaration classOfMethod = (ClassOrInterfaceDeclaration) method.getParentNode();
            if (FILTER_METHODS.contains(method.getName())
                    || FILTER_METHODS.contains(classOfMethod.getName() + "." + method.getName())) {
                return method.getType();
            }

            String typeAsString = method.getType().toStringWithoutComments().trim();
            if (typeAsString.equals("void")) {
                typeAsString = "Void";
            }

            return new ReferenceType(new ClassOrInterfaceType("Executions<" + typeAsString + ">"));
        };
    }

    /**
     * Supply addititional imports.
     *
     * @return
     */
    protected Supplier<List<String>> importSupplier() {
        return () -> Collections.emptyList();
    }

    @Test
    public void createInterface() throws Exception {
        factory.createInterface();
    }
}
