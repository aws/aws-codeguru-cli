# CodeGuru Reviewer CLI Wrapper
Simple CLI wrapper for CodeGuru reviewer that provides a one-line command to scan a local clone of a repository and
receive results. 

### Before you start

Before we start, let's make sure that you can access an AWS account from your computer. 
Follow the credentials setup process for the for the [AWS CLI](https://github.com/aws/aws-cli#configuration).
The credentials must have permissions to use CodeGuru Reviewer and S3.

### Download the CLI and scan an Example

TODO ... after we have the release github action



### Running from CI/CD

You can use this CLI to run CodeGuru from inside your CI/CD pipeline. See [this action](.github/workflows/self-test-and-release.yml#L30-L41) as an example. First, you need credentials for a role with the following permissions in your container:

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
                "codeguru-reviewer:ListCodeReviews",
                "codeguru-reviewer:DescribeCodeReview",
                "codeguru-reviewer:ListRecommendations"
            ],
            "Resource": "*",
            "Effect": "Allow"
        },
        {
            "Action": [
                "s3:CreateObject*",
                "s3:GetObject*",
                "s3:GetBucket*",
                "s3:List*",
                "s3:DeleteObject*",
                "s3:PutObject",
                "s3:Abort*"
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


```
aws-codeguru-cli --region [BUCKET REGION] --no-prompt ...
```

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
