# zorechka-bot
Github bot for keeping your Bazel dependencies up-to-date

It's like Scala Steward but for Bazel.

# Configuration

There is a repos.md file for list of repos to check. The format is "{username}/{repo} {token}" per line. Token is optional, requred for private repos. To get a valid token, go to https://github.com/settings/tokens

# How to run

Use sdb and docker to build and run the application. From project dir, run:

    sbt assembly
    docker build -t zorechka-bot ./
    docker run --rm -it zorechka-bot
    
# How to contribute

Fork and import as SBT project. 
