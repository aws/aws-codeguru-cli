# CodeGuru Reviewer CLI Wrapper
Simple CLI wrapper for CodeGuru reviewer that provides a one-line command to scan a local clone of a repository and
receive results. This CLI wraps the [AWS CLI](https://aws.amazon.com/cli/) commands to communicate with 
[AWS CodeGuru Reviewer](https://aws.amazon.com/codeguru/). Using CodeGuru Reviewer may generate metering fees
in your AWS account. See the [CodeGuru Reviewer pricing](https://aws.amazon.com/codeguru/pricing/) for details.

### Table of Contents
- [Installation](#installation)
- [Using the CLI](#using-the-cli)
- [Suppressing Recommendations](#suppressing-recommendations)
- [Running from CI/CD](#running-from-cicd)
- [Security](#security)
- [License](#license)

## Installation

### Prerequisites

To run the CLI, we need to have a version of git, Java (e.g., [Amazon Corretto](https://aws.amazon.com/corretto/?filtered-posts.sort-by=item.additionalFields.createdDate&filtered-posts.sort-order=desc)) 
and the [AWS Command Line interface](https://aws.amazon.com/cli/) installed. 
Verify that both applications are installed on our machine by running:

```
java -version
mvn --version
aws --version
git --version
```

We will also need working credentials on our machine to interact with our AWS account. 
Learn more about setting up credentials for AWS here: https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-configure.html.

You can always use the CLI with *Admin* credentials but if you want to have a specific role to use the CLI, your
 credentials must have at least the following permissions:

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
                "codeguru-reviewer:ListRecommendations",
                "iam:CreateServiceLinkedRole"
            ],
            "Resource": "*",
            "Effect": "Allow"
        },
        {
            "Action": [
                "s3:CreateBucket",
                "s3:GetBucket*",
                "s3:List*",
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject"
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


## Using the CLI

You can download the [aws-codeguru-cli](https://github.com/aws/aws-codeguru-cli/releases/latest) from the releases section.
Download the latest version and add it to your `PATH`:
```
curl -OL https://github.com/aws/aws-codeguru-cli/releases/download/0.1.0/aws-codeguru-cli.zip
unzip aws-codeguru-cli.zip
export PATH=$PATH:./aws-codeguru-cli/bin
```



### Scan an Example

Now, let's download an example project (requires Maven):
```
git clone https://github.com/aws-samples/amazon-codeguru-reviewer-sample-app
cd amazon-codeguru-reviewer-sample-app
mvn clean compile
```
After compiling, we can run CodeGuru with:
```
aws-codeguru-cli --root-dir ./ --build target/classes --src src --output ./output
open output/codeguru-report.html 
```
where `--root-dir .` specifies that the root of the project that we want to analyze. The option `--build target/classses` states that the build artifacts are located under `./target/classes` and `--src` says that we only want to analyze source files that are
located under `./src`. The option `--output ./output` specifies where CodeGuru should write its recommendations to. By default,
CodeGuru produces a Json and Html report.

You can provide your own bucket name using the `--bucket-name` option. Note that, currently, CodeGuru Reviewer only
supports bucket names that start with the prefix `codeguru-reviewer-` out of the box. If you choose a different naming
pattern for your bucket you need to:
1. Grant `S3:GetObject` permissions on the S3 bucket to `codeguru-reviewer.amazonaws.com`
2. If you are using SSE in the S3 bucket, grant `KMS::Decrypt` permissions to `codeguru-reviewer.amazonaws.com`

### Using Encryption

CodeGuru Reviewer allows you to use a customer managed key (CMCMK) to encrypt the contents of the S3 bucket that is used 
to store source and build artifacts, and all metadata and recommendations that are produced by CodeGuru Reviewer. 
First, create a customer managed key in KMS.
You will need to grant CodeGuru Reviewer permission to decrypt artifacts with this key by adding the 
following Statement to your Key policy:

```json
{
    "Sid": "Allow CodeGuru to use the key to decrypt artifacts",
    "Effect": "Allow",
    "Principal": {
        "AWS": "*"
    },
    "Action": [
        "kms:Decrypt",
        "kms:DescribeKey"
    ],
    "Resource": "*",
    "Condition": {
        "StringEquals": {
            "kms:ViaService": "codeguru-reviewer.amazonaws.com",
            "kms:CallerAccount": [Your AWS ACCOUNT ID]
        }
    }
}
```
Then, enable server-side encryption for the bucket that you are using with CodeGuru Reviewer. The bucket name should be
`codeguru-reviewer-cli-[YOUR ACCOUNT]-[YOUR REGION]`, unless you provided a custom name. For encryption, use the
KMS key that you created in the previous step.

Now you can analyze a repository by providing the KMS key ID (not the alias). For example:
```
 aws-codeguru-cli -r ./ -kms 12345678-abcd-abcd-1234-1234567890ab
```
The first time you analyze a repository with the CodeGuru Reviewer CLI, a new association will be created and
the provided key will be associated with this repository. Fur subsequent scans, you do not need to provide the 
key again. Note that you can start using a key after the repository is already associated. If you want to switch
from not using a key to using a key, you need to delete the existing association first in the AWS Console and
then trigger a new scan with the CLI where you provide the key.


## Suppressing Recommendations

The CodeGuru Reviewer CLI searches for a file named `.codeguru-ignore.yml` where users can specify criteria
based on which recommendations should be suppressed. Suppressed recommendations will not be returned by the CLI,
but still show up in the AWS console.

The `.codeguru-ignore.yml` file can use any of the filter criteria shown below:

```yaml
version: 1.0  # The Version field is mandatory. All other fields are optional. 

# The CodeGuru Reviewer CLI produces a recommendations.json file which contains deterministic IDs for each
# recommendation. This ID can be excluded so that this recommendation will not be reported in future runs of the
# CLI.
ExcludeById:
- '4d2c43618a2dac129818bef77093730e84a4e139eef3f0166334657503ecd88d'

# We can tell the CLI to exclude all recommendations below a certain severity. This can be useful in CI/CD integration.
ExcludeBelowSeverity: 'HIGH'

# We can exclude all recommendations that have a certain tag. Available Tags can be found here:
# https://docs.aws.amazon.com/codeguru/detector-library/java/tags/
# https://docs.aws.amazon.com/codeguru/detector-library/python/tags/
ExcludeTags:
  - 'maintainability'

# We can also exclude recommendations by Detector ID. Detector IDs can be found here:
# https://docs.aws.amazon.com/codeguru/detector-library
ExcludeRecommendations:
# Ignore all recommendations for a given Detector ID 
  - detectorId: 'java/aws-region-enumeration@v1.0'
# Ignore all recommendations for a given Detector ID in a provided set of locations.
# Locations can be written as Unix GLOB expressions using wildcard symbols.
  - detectorId: 'java/aws-region-enumeration@v1.0'
    Locations:
      - 'src/main/java/com/folder01/*.java'

# Excludes all recommendations in the provided files. Files can be provided as Unix GLOB expressions.
ExcludeFiles:
  - tst/**

```

Only the `version` field is mandatory in the `.codeguru-ignore.yml` file. All other entries are optional, and
the CLI will understand any combination of those entries.

An example of such a configuration file can be found [here](https://github.com/aws/aws-codeguru-cli/blob/main/.codeguru-ignore.yml).

## Running from CI/CD

You can use this CLI to run CodeGuru from inside your CI/CD pipeline. 
See [this action](.github/workflows/cicd-demo.yml) as an example. To use the CLI in CI/CD, you need working credentials.
You can use this [CDK template](https://github:com/aws-samples/aws-codeguru-reviewer-cicd-cdk-sample) to set up OIDC credentials for Github Actions.

Then you can run the CLI in non-interactive mode using the `--no-prompt` option, and use the option
`--fail-on-recommendations` to return a non-zero exit code if recommendations are reported.
You can specify a region and  AWS profile using the `--region` and `--profile` options as needed:
```
aws-codeguru-cli --region [BUCKET REGION] --no-prompt  --fail-on-recommendations -r ./ ...
```
obtain the commit range works differently for different CI/CD providers. For example, GitHub provides the relevant
commits via environment variables such as `${{ github.event.before }}` and `${{ github.event.after }}`.

An end-to-end example is provided in [this action](.github/workflows/cicd-demo.yml).

### Build from Source

To build the project, you need Java 8 or later. Checkout this repository and run:
```
./gradlew installDist
```
and now run your local build with:
```
./build/install/aws-codeguru-cli/bin/aws-codeguru-cli
```
You can run a self-test with:
```
./build/install/aws-codeguru-cli/bin/aws-codeguru-cli -r . -s src/main/java -b build/libs -c HEAD^:HEAD
```

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.
