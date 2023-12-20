The repository demonstrate how to specify compiler flags for individual files (or group of files).
Gradle core native compile task doesn't allow specifying compile flags for each individual files.
The solution is to "clone" the compile flag and split the files that we want to specify compile flags to each compile tasks.

The `compileFlags` extensions allow specifying compile flags per `File` spec.
We can add more helper method to match any common use cases.

See `app/build.gradle`.
