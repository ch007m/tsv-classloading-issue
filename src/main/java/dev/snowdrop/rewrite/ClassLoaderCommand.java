package dev.snowdrop.rewrite;

import dev.snowdrop.rewrite.utils.ClassLoaderUtils;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static dev.snowdrop.rewrite.utils.JavaParserCaller.findCaller;

@TopCommand
@CommandLine.Command(name = "rewrite")
public class ClassLoaderCommand implements Runnable {
    private static final Logger logger = Logger.getLogger(ClassLoaderCommand.class.getName());
    public static final String DEFAULT_RESOURCE_PATH = "META-INF/rewrite/classpath.tsv.gz";

    @CommandLine.Option(
            names = {"--jar"},
            description = "Additional JAR files containing recipes (file paths or Maven GAV coordinates, can be specified multiple times or comma-separated)",
            split = ",",
            defaultValue = "org.openrewrite.recipe:rewrite-java-dependencies:1.51.1"
    )
    List<String> additionalJarPaths = new ArrayList<>();

    @Override
    public void run() {
        try {
            ClassLoader appClassLoader = ClassLoaderCommand.class.getClassLoader();
            URLClassLoader extendedLoader = null;

            Class<?> clazz = appClassLoader.loadClass("org.openrewrite.java.JavaParser");
            logger.infof("Class found: %s in classloader: %s. %n",clazz.getName(),clazz.getClassLoader());

            ClassLoaderUtils clu = new ClassLoaderUtils();
            if (!additionalJarPaths.isEmpty())
                extendedLoader = clu.loadAdditionalJars(additionalJarPaths, appClassLoader);

            assert extendedLoader != null;

            logger.infof("=== We can find classpath.tsv.gz file using the URLClassLoader within the additional JAR loaded :-)");
            for (Enumeration<URL> e = extendedLoader.getResources(DEFAULT_RESOURCE_PATH); e.hasMoreElements(); ) {
                logger.infof("=== Resource found here: %s", e.nextElement());
            }

            ClassLoader javaParserClassLoader = findCaller().getClassLoader();
            logger.infof("JavaParser class found in classloader: %s.",javaParserClassLoader);
            logger.warn("=== We cannot find classpath.tsv.gz file using the ClassLoader of the JavaParser class :-(");
            for (Enumeration<URL> e = javaParserClassLoader.getResources(DEFAULT_RESOURCE_PATH); e.hasMoreElements(); ) {
                logger.warnf("=== Resource found here : %s", e.nextElement());
            }

        } catch (ClassNotFoundException | IOException e) {
            logger.error(e);
        }
    }
}
