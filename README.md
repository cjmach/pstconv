# pstconv

A java command line tool to convert proprietary Microsoft Outlook OST/PST files 
to EML or MBOX format, even if the file is password protected. OST/PST content is parsed and extracted with [java-libpst](https://github.com/rjohnsondev/java-libpst) library.



# Requirements

- Java Runtime Environment 8

# Usage

```console
usage: java -jar pstconv.jar [OPTIONS]
 -e,--encoding <ENCODING>   Encoding to use for reading character data.
                            Default is ISO-8859-1.
 -f,--format <FORMAT>       Convert input file to one of the following
                            formats: mbox, eml. Default is mbox.
 -h,--help                  Print help and exit.
 -i,--input <FILE>          Path to OST/PST input file. Required option.
 -o,--output <DIRECTORY>    Path to MBOX/EML output directory. If it
                            doesn't exist, the application will attempt to
                            create it. Required option.
 -v,--version               Print version and exit.
```

For example, the following command will convert File01.pst to MBOX format, saving the results to a directory named "mailbox":

```console
$ java -jar pstconv.jar -i File01.pst -o mailbox
```

After the conversion is finished, you can use a free software like [Mozilla Thunderbird](https://www.thunderbird.net/) in combination with [ImportExportTools NG](https://addons.thunderbird.net/en-US/thunderbird/addon/importexporttools-ng/) add-on to import the "mailbox" directory to the e-mail client mailbox and view the converted messages.

The tool adds a custom header named 'X-Outlook-Descriptor-Id' to each converted message containing the value of the descriptor id from the original PST message, so that's possible to compare both messages if needed.

# How it works

The following flowchart diagram tries to explain the sequence of steps taken by pstconv tool to convert the input PST file.

![pstconv flowchart](doc/pstconv-flowchart.svg)

# Performance

We have randomly selected 12 PST files from real forensic cases with sizes ranging from 200MB to more than 3GB. We ran the pstconv tool 20 times to convert each selected file to MBOX and EML format (10 times each). The average results are shown in the following tables.

### MBOX

| File Name  | Size (MB) | Msg Count | Time (sec)     | Msgs/sec       |
|   :---     |    ---:   |    ---:   |    ---:        |   ---:         |
| File01.pst | 181       | 743       | 6.9            | 107.3          |
| File02.pst | 299       | 1126      | 11.6           | 96.8           |
| File03.pst | 554       | 978       | 19.3           | 50.7           |
| File04.pst | 632       | 1198      | 21.2           | 56.4           |
| File05.pst | 770       | 1388      | 21.0           | 66.1           |
| File06.pst | 1033      | 3045      | 34.8           | 87.6           |
| File07.pst | 1162      | 4393      | 36.1           | 121.6          |
| File08.pst | 1365      | 3122      | 41.7           | 74.8           |
| File09.pst | 1849      | 3432      | 59.7           | 57.5           |
| File10.pst | 1979      | 10460     | 68.1           | 153.6          |
| File11.pst | 2771      | 2745      | 89.6           | 30.7           |
| File12.pst | 3477      | 3451      | 115.0          | 30.0           |
| **Total**  | **16072** | **36081** | **525.0**      | **77.8 (avg)** |

### EML

| File Name  | Size (MB) | Msg Count | Time (sec)     | Msgs/sec       |
|   :---     |    ---:   |    ---:   |    ---:        |   ---:         |
| File01.pst | 181       | 743       | 42.5           | 17.5           |
| File02.pst | 299       | 1126      | 67.9           | 16.6           |
| File03.pst | 554       | 978       | 134.7          | 7.3            |
| File04.pst | 632       | 1198      | 139.6          | 8.6            |
| File05.pst | 770       | 1388      | 151.5          | 9.2            |
| File06.pst | 1033      | 3045      | 224.8          | 13.6           |
| File07.pst | 1162      | 4393      | 238.6          | 18.4           |
| File08.pst | 1365      | 3122      | 274.9          | 11.4           |
| File09.pst | 1849      | 3432      | 392.0          | 8.8            |
| File10.pst | 1979      | 10460     | 424.2          | 24.7           |
| File11.pst | 2771      | 2745      | 611.0          | 4.5            |
| File12.pst | 3477      | 3451      | 762.4          | 4.5            |
| **Total**  | **16072** | **36081** | **3464.2**     | **12.1 (avg)** |

The performance results show that converting a PST file to MBOX format is about 6.5 times faster than converting to EML format.

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
$ mvn -B release:prepare -DignoreSnapshots=true
$ mvn -B release:perform -Darguments='-Dmaven.deploy.skip=true' 
```

It will automatically assume the defaults for each required parameter, namely,
`releaseVersion` and `developmentVersion`. If it's necessary to control the values 
of each version, the `release:prepare` command can be run as follows:

```console
$ mvn -B release:prepare -DignoreSnapshots=true -DreleaseVersion={a release version} -DdevelopmentVersion={next version}-SNAPSHOT
```