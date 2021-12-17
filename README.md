# CodeGuru Reviewer CLI Wrapper
Simple CLI wrapper for CodeGuru reviewer that provides a one-line command to scan a local clone of a repository and
receive results. 

### Before you start

Before we start, let's make sure that you can access an AWS account from your computer. 
Follow the credentials setup process for the for the [AWS CLI](https://github.com/aws/aws-cli#configuration).
The credentials must have permissions to use CodeGuru Reviewer and S3.

### Download the CLI and scan an Example

TODO ... after we have the release github action



### Build from Source

To build the project, you need Java 8 or later. Checkout this repository and run:
```
./gradlew installDist
```
and now run your local build with:
```
./build/install/aws-codeguru-cli/bin/aws-codeguru-cli
```
you can run a self-test with:
```
./build/install/aws-codeguru-cli/bin/aws-codeguru-cli -r . -s src/main/java -b build/libs -c HEAD^:HEAD
```
