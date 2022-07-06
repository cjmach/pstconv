# pstconv

A java command line tool to convert proprietary Microsoft Outlook OST/PST files 
to EML or MBOX format, even if the file is password protected. OST/PST content is 
parsed and extracted with [java-libpst](https://github.com/rjohnsondev/java-libpst)
library.

# Requirements

- Java Runtime Environment 8

# Usage

```console
usage: java -jar pstconv.jar [OPTIONS]
 -e,--encoding <ENCODING>   Encoding to use for reading character data.
                            Default is UTF-8.
 -f,--format <FORMAT>       Convert input file to one of the following
                            formats: mbox, eml. Default is eml.
 -h,--help                  Print help and exit.
 -i,--input <FILE>          Path to OST/PST input file. Required option.
 -o,--output <DIRECTORY>    Path to Mbox/EML output directory. If it
                            doesn't exist, the application will attempt to
                            create it. Required option.
 -v,--version               Print version and exit.
```

# Building

To build this project you need:

- Java Development Kit 8
- Apache Maven 3.6.x

Assuming all the tools can be found on the PATH, simply go to the project 
directory and run the following command:

```console
$ mvn -B package
```

# Releasing

Go to the project directory and run the following commands:

```console
$ mvn -B release:prepare
$ mvn -B release:perform -Darguments='-Dmaven.deploy.skip=true' 
```

It will automatically assume the defaults for each required parameter, namely,
`releaseVersion` and `developmentVersion`. If it's necessary to control the values 
of each version, the `release:prepare` command can be run as follows:

```console
$ mvn -B release:prepare -DreleaseVersion={a release version} -DdevelopmentVersion={next version}-SNAPSHOT
```