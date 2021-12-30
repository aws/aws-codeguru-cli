# CodeGuru Reviewer CLI Wrapper
Simple CLI wrapper for CodeGuru reviewer that provides a one-line command to scan a local clone of a repository and
receive results. This CLI wraps the [AWS CLI](https://aws.amazon.com/cli/) commands to communicated with 
[AWS CodeGuru Reviewer](https://aws.amazon.com/codeguru/). Using CodeGuru Reviewer may generated metering fees
in your AWS account. See the [CodeGuru Reviewer pricing](https://aws.amazon.com/codeguru/pricing/) for details.

### Before you start

Before we start, let's make sure that you can access an AWS account from your computer. 
Follow the credentials setup process for the for the [AWS CLI](https://github.com/aws/aws-cli#configuration).
The credentials must have at least the following permissions:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "codeguru-reviewer:ListRepositoryAssociations",
                "codeguru-reviewer:AssociateRepository",
                "codeguru-reviewer:DescribeRepositoryAssociation",
                "codeguru-reviewer:CreateCodeReview",
                "codeguru-reviewer:DescribeCodeReview",
                "codeguru-reviewer:ListRecommendations"
            ],
            "Resource": "*",
            "Effect": "Allow"
        },
        {
            "Action": [
                "s3:CreateBucket*",
                "s3:GetObject*",
                "s3:PutObject"
            ],
            "Resource": [
                "arn:aws:s3:::codeguru-reviewer-cli-*",
                "arn:aws:s3:::codeguru-reviewer-cli-*/*"
            ],
            "Effect": "Allow"
        }
    ]
}
```


### Download the CLI and scan an Example

You can download the [aws-codeguru-cli](releases/download/latest/aws-codeguru-cli.zip) from the releases section.
Download the latest version and add it to your `PATH`:
```
curl -OL https://github.com/martinschaef/aws-codeguru-cli/releases/download/latest/aws-codeguru-cli.zip
unzip aws-codeguru-cli.zip
export PATH=$PATH:./aws-codeguru-cli/bin
```

Now, lets download an example project (requires Maven):
```
git clone https://github.com/aws-samples/amazon-codeguru-reviewer-sample-app
cd amazon-codeguru-reviewer-sample-app
mvn clean compile
```
After compiling, we can run CodeGuru with:
```
aws-codeguru-cli --repository ./ --build target/classes --src src --output ./output
open output/codeguru-report.html 
```
where `--repository .` specifies that the *repository* that we want to analyze is the current directory `./`. The option `--build target/classses` states that the build artifacts are located under `./target/classes` and `--src` says that we only want to analyze source files that are
located under `./src`. The option `--output ./output` specifies where CodeGuru should write its recommendations to. By default,
CodeGuru produces a Json and Html report.


### Running from CI/CD

You can use this CLI to run CodeGuru from inside your CI/CD pipeline. See [this action](.github/workflows/self-test-and-release.yml#L30-L41) as an example. First, you need credentials for a role with the permissions mentioned above. If you already scanned
the repository once with the CLI, the S3 bucket has been created, and the you do not need the `s3:CreateBucket*` permission anymore.

Then you can run the CLI in non-interactive mode using the `--no-prompt` option. Further, you can specify a region and 
AWS profile using the `--region` and `--profile` options as needed:
```
aws-codeguru-cli --region [BUCKET REGION] --no-prompt -r ./ ...
```
obtain the commit range works differently for different CI/CD providers. For example, GitHub provides the relevant
commits via environment variables such as `${{ github.event.before }}` and `${{ github.event.after }}`.

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

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.
