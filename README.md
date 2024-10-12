PerseveranceHarvester
=====================
![Perseverance](https://i.imgur.com/ExA4dY8.png "Image Credit: NASA/JPL-Caltech")

What is it?
-----------
**PerseveranceHarvester** is a **standalone Java program** that downloads the raw images taken by the Mars rover **Perseverance** that are available on the **NASA** website at the following URL: <https://mars.nasa.gov/mars2020/multimedia/raw-images/>

Requirements
------------
Running **PerseveranceHarvester** requires **Java 17** at least.

Packaging
---------
Create an uber-jar containing all the dependencies by executing the following command:
```
mvn clean package
```

Executing
---------
Launch the harvester by executing the following command:
```
java -jar perseverance-harvester-X.Y.Z.jar -d /path/to/save/root/dir
```

Usage
-----
```
NAME
        perseverance-harvester - Perseverance raw images harvester command

SYNOPSIS
        perseverance-harvester [ {-d | --dir} <saveRootDirectory> ]
                [ {-f | --fromPage} <fromPage> ] [ --force ] [ {-h | --help} ]
                [ --stop-at-already-downloaded-page ]
                [ {-t | --toPage} <toPage> ]
                [ --threads <downloadThreadsNumber> ]

OPTIONS
        -d <saveRootDirectory>, --dir <saveRootDirectory>
            Root directory in which the images are saved

        -f <fromPage>, --fromPage <fromPage>
            Harvesting starts from this page

        --force
            Force harvesting already downloaded images

        -h, --help
            Display help information

        --stop-at-already-downloaded-page
            Harvesting stops at the first page which is already fully
            downloaded

        -t <toPage>, --toPage <toPage>
            Harvesting stops at this page

        --threads <downloadThreadsNumber>
            Number of threads to download the images (default is 4)
```