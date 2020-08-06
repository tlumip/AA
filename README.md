We build the JAR files in Eclipse. We load the files into a project and export
JARs (File -> Export -> JAR file, then uncheck everything except src/java).

The projects have the following dependencies:

- swim2 (oregonPP_rXXXX.jar) depends on
    - pecas
    - common-base
- pecas (PecasV2.10_rXXXX.jar) depends on
    - common-base
    - SimpleOrmProject
    - Matrix Toolkits Java (mtj.jar)
- Everything depends on Apache Log4j 1.2 (log4j-1.2.9.jar)