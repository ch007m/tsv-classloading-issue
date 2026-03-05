## Project reproducing the classloading issue of classpath.tsv.gz

This project reproduces the issue that we observe to load the resource `classpath.tsv.gz` packaged under `META-INF/rewrite`
of, by example, the following GAV `org.openrewrite.recipe:rewrite-java-dependencies:1.51.1` using an URLClassloader extending the `AppClassloader`.

The problem takes place when OpenRewrite runs a recipe like `ReplaceAnnotation` where they access on the `JavaParser` class the method `classpathFromResources(Context,String )` 
able to search about such a resource. 

As you can see hereafter the current code uses as ClassLoader the classloader which loaded the `JavaParser` class
and not at all the URLClassloader created using Environment and `Environment.Collection<? extends ResourceLoader> resourceLoaders;`

```java
// org.openrewrite.java.ReplaceAnnotation
a = JavaTemplate.builder(annotationTemplateToInsert)
                        .javaParser(classpathResourceName == null ?
        JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()) :
        JavaParser.fromJavaVersion().classpathFromResources(ctx, classpathResourceName)) // HERE
...
// JavaParser class
public B classpathFromResources(ExecutionContext ctx, String... classpath) {
    this.artifactNames = emptyList();
    this.classpath = dependenciesFromResources(ctx, classpath);
    return (B) this;
}
...
static List<Path> dependenciesFromResources(ExecutionContext ctx, String... artifactNamesWithVersions) {
    if (artifactNamesWithVersions.length == 0) {
        return emptyList();
    }
    List<Path> artifacts = new ArrayList<>(artifactNamesWithVersions.length);
    Set<String> missingArtifactNames = new LinkedHashSet<>(Arrays.asList(artifactNamesWithVersions));
    List<String> availableArtifacts = new ArrayList<>();

    TypeTable typeTable = TypeTable.fromClasspath(ctx, missingArtifactNames);
    ...

// TypeTable class    
    public static @Nullable TypeTable fromClasspath(ExecutionContext ctx, Collection<String> artifactNames) {
        try {
            ClassLoader classLoader = findCaller().getClassLoader();
            // The ClassLoader we got it here is the AppClassLoader !!
```

To reproduce the issue, execute the following command:
```bash
mvn quarkus:dev
```
and look the messages logged:
```bash
// JavaParser class is well loaded by the AppClassLoader of Quarkus
2026-03-05 14:30:47,133 INFO  [dev.sno.rew.ClassLoaderCommand] (Quarkus Main Thread) Class found: org.openrewrite.java.JavaParser in classloader: QuarkusClassLoader:Quarkus Base Runtime ClassLoader: DEV for tsv-classloading-issue-0.1.0-SNAPSHOT@23bb8443. 

// The addtional GAV has been added to a new URLClassLoader
2026-03-05 14:30:47,187 INFO  [dev.sno.rew.uti.ClassLoaderUtils] (Quarkus Main Thread) Loaded additional JAR: /Users/cmoullia/.m2/repository/org/openrewrite/recipe/rewrite-java-dependencies/1.51.1/rewrite-java-dependencies-1.51.1.jar

2026-03-05 14:40:50,211 INFO  [dev.sno.rew.ClassLoaderCommand] (Quarkus Main Thread) === We can find classpath.tsv.gz file using the URLClassLoader within the additional JAR loaded :-)
2026-03-05 14:40:50,212 INFO  [dev.sno.rew.ClassLoaderCommand] (Quarkus Main Thread) === Resource found here: jar:file:/Users/cmoullia/.m2/repository/org/openrewrite/recipe/rewrite-spring-to-quarkus/0.6.0/rewrite-spring-to-quarkus-0.6.0.jar!/META-INF/rewrite/classpath.tsv.gz

2026-03-05 14:40:50,215 INFO  [dev.sno.rew.ClassLoaderCommand] (Quarkus Main Thread) JavaParser class found in classloader: QuarkusClassLoader:Quarkus Base Runtime ClassLoader: DEV for tsv-classloading-issue-0.1.0-SNAPSHOT@23bb8443.
2026-03-05 14:40:50,215 WARN  [dev.sno.rew.ClassLoaderCommand] (Quarkus Main Thread) === We cannot find classpath.tsv.gz file using the ClassLoader of the JavaParser class :-(
```

To debug and use the remote debugger, execute this command
```bash
mvn quarkus:dev -Dsuspend
```