# CodeGuru Reviewer CLI Wrapper
Simple CLI wrapper for CodeGuru reviewer that provides a one-line command to scan a local clone of a repository and
receive results. This CLI wraps the [AWS CLI](https://aws.amazon.com/cli/) commands to communicated with 
[AWS CodeGuru Reviewer](https://aws.amazon.com/codeguru/). Using CodeGuru Reviewer may generate metering fees
in your AWS account. See the [CodeGuru Reviewer pricing](https://aws.amazon.com/codeguru/pricing/) for details.

### Prerequisites

To run the CLI, we need to have a version of git, Java (e.g., [Amazon Corretto](https://aws.amazon.com/corretto/?filtered-posts.sort-by=item.additionalFields.createdDate&filtered-posts.sort-order=desc)) and the [AWS Command Line interface](https://aws.amazon.com/cli/) installed. Verify that both application are installed on our machine by running:

```
java -version
mvn --version
aws --version
git --version
```

We will also need working credentials on our machine to interact with our AWS account. Learn more about setting up credentials for AWS here: https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-configure.html. 
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


### Download the CLI and scan an Example

You can download the [aws-codeguru-cli](https://github.com/aws/aws-codeguru-cli/releases/latest) from the releases section.
Download the latest version and add it to your `PATH`:
```
curl -OL https://github.com/aws/aws-codeguru-cli/releases/download/0.0.1/aws-codeguru-cli.zip
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
You can run a self-test with:
```
./build/install/aws-codeguru-cli/bin/aws-codeguru-cli -r . -s src/main/java -b build/libs -c HEAD^:HEAD
```

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.
