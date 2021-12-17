# CodeGuru Reviewer CLI Wrapper
Simple CLI wrapper for CodeGuru reviewer that provides a one-line command to scan a local clone of a repository and
receive results. 

### Before you start

Before we start, let's make sure that you can access an AWS account from your computer. 
We recommend using the ADA Cli to set things up. If you do not have the ada command on your path, install ada first:

```bash
mwinit -o
toolbox install ada
```

after installing ada, let's set up a the credentials to your account. E.g.:
```bash
ada credentials update --account 727015569506 --provider isengard --role Admin --profile default --once
```
Pulls the Admin credentials for my personal account `727015569506` into the default profile. If you use the default
profile, you don't have to specify a profile when running the CLI.


### Check out the CLI and run a scan.

First build the thing:
```bash
brazil ws create -n gurucli -vs GuruReviewerBuildIntegration/development
cd gurucli
bws use -p GuruReviewerCliWrapper --platform AL2_x86_64
cd src/GuruReviewerCliWrapper
git checkout github
brazil-build
```


Now run the command below.
```bash
./build/bin/gurureviewer.sh -r . -s src/main -b build/private/gradle/classes/java/main 
```
Output should look something like this:
```
Starting analysis of /Users/schaef/workspaces/cli/src/GuruReviewerCliWrapper with association arn:aws:codeguru-reviewer:us-east-1:727015569506:association:fa31e430-3ccc-4b08-8885-d93c8f6cef66 and S3 bucket codeguru-reviewer-cli-727015569506-us-east-1
.............................................................................................................:)
Dropping finding because file not found on disk: /Users/schaef/workspaces/cli/src/GuruReviewerCliWrapper/.
Report written to: file:///Users/schaef/workspaces/cli/src/GuruReviewerCliWrapper/./code-guru/codeguru-report.html
```
Now you can see findings in `./code-guru/codeguru-report.html`.

You can use the `--profile` and `--region` options to run with different credentials or regions. 

If you want to just analyze the last commit, try the `-c` option:
```bash
./build/bin/gurureviewer.sh -r . -s src/main -b build/private/gradle/classes/java/main -c HEAD^:HEAD
```

### Build from Source

To build the project, you need Java 8 or later. Checkout this repository and run:
```
./gradlew installDist

```

