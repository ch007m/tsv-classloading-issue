## Project reproducing the classloading issue of classpath.tsv.gz

This project reproduces the issue that we observe to load the resource `classpath.tsv.gz` packaged under `META-INF/rewrite`
of by example the following GAV `org.openrewrite.recipe:rewrite-java-dependencies:1.51.1` using an URLClassloader extending the AppClassloader.

The problem takes place when OpenRewrite runs a recipe like `ReplaceAnnotation` where we access on the `JavaParser` class the method `classpathFromResources(Context,String )` 
able to search about such a resource. As you can see hereafter the current code uses as ClassLoader the classloader having loaded `JavaParser` class
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

```