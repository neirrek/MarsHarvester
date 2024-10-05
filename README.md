PerseveranceHarvester
=====================
![Perseverance](https://i.imgur.com/ExA4dY8.png "Image Credit: NASA/JPL-Caltech")

What is it?
-----------
**PerseveranceHarvester** is a **standalone Java program** to download the raw images taken by the Mars rover **Perseverance** which are available on the **NASA** website at the following URL: <https://mars.nasa.gov/mars2020/multimedia/raw-images/>

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
        perseverance-harvester [ --convert-to-jpg <jpgCompressionRatio> ]
                [ {-d | --dir} <saveRootDirectory> ]
                [ {-f | --fromPage} <fromPage> ]
                [ --force ]
                [ {-h | --help} ]
                [ {-s | --stop-after-already-downloaded-pages} <stopAfterAlreadyDownloadedPages> ]
                [ {-t | --toPage} <toPage> ]
                [ --threads <downloadThreadsNumber> ]

OPTIONS
        --convert-to-jpg <jpgCompressionRatio>
            Convert the downloaded images to JPG format with the given
            compression ratio (default is not to convert when this option is
            missing

            This options value must fall in the following range: 1 <= value <= 100


        -d <saveRootDirectory>, --dir <saveRootDirectory>
            Root directory in which the images are saved

        -f <fromPage>, --fromPage <fromPage>
            Harvesting starts from this page (default is page 1 when this
            option is missing)

            This options value must fall in the following range: value >= 1


        --force
            Force harvesting already downloaded images

        -h, --help
            Display help information

        -s <stopAfterAlreadyDownloadedPages>,
        --stop-after-already-downloaded-pages <stopAfterAlreadyDownloadedPages>
            Harvesting stops after the nth page which is already fully
            downloaded (default is not to stop when this option is missing)

            This options value must fall in the following range: value >= 1


        -t <toPage>, --toPage <toPage>
            Harvesting stops at this page (default is last page when this
            option is missing)

            This options value must fall in the following range: value >= 1


        --threads <downloadThreadsNumber>
            Number of threads to download the images (default is 4 when this
            option is missing)

            This options value must fall in the following range: value >= 1
```