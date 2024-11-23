MarsHarvester
=====================
![Perseverance](https://i.imgur.com/ExA4dY8.png "Image Credit: NASA/JPL-Caltech")

What is it?
-----------
**MarsHarvester** is a **standalone Java program** to download the raw images taken by the Mars rovers **Perseverance** and **Curiosity** which are available on the **NASA** website at the following URL:
- **Perseverance:** <https://mars.nasa.gov/mars2020/multimedia/raw-images/>
- **Curiosity:** <https://mars.nasa.gov/msl/multimedia/raw-images/>

Requirements
------------
Running **MarsHarvester** requires **Java 17** at least.

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
java -jar mars-harvester-X.Y.Z.jar --mission <PERSEVERANCE|CURIOSITY> -dir /path/to/save/root/dir
```

Usage
-----
```
NAME
        mars-harvester - Mars rovers raw images harvester command

SYNOPSIS
        mars-harvester [ --convert-to-jpg <jpgCompressionRatio> ]
                [ {-d | --dir} <saveRootDirectory> ]
                [ {-f | --fromPage} <fromPage> ]
                [ --force ] [ {-h | --help} ]
                [ {-m | --mission} <mission> ]
                [ {-s | --stop-after-already-downloaded-pages} <stopAfterAlreadyDownloadedPages> ]
                [ {-t | --toPage} <toPage> ]
                [ --threads <downloadThreadsNumber> ]

OPTIONS
        --convert-to-jpg <jpgCompressionRatio>
            Convert the downloaded images to JPG format with the given
            compression ratio (default is not to convert when this option is
            missing

            This option may occur a maximum of 1 times


            This options value must fall in the following range: 1 <= value <= 100


        -d <saveRootDirectory>, --dir <saveRootDirectory>
            Root directory in which the images are saved

            This option may occur a maximum of 1 times


            This options value must be a path to a directory. The provided path
            must exist on the file system. The provided path must be readable
            and writable.


        -f <fromPage>, --fromPage <fromPage>
            Harvesting starts from this page (default is page 1 when this
            option is missing)

            This option may occur a maximum of 1 times


            This options value must fall in the following range: value >= 1


        --force
            Force harvesting already downloaded images

            This option may occur a maximum of 1 times


        -h, --help
            Display help information

        -m <mission>, --mission <mission>
            Name of the Mars mission

            This options value is restricted to the following set of values:
                CURIOSITY
                PERSEVERANCE

            This option may occur a maximum of 1 times


        -s <stopAfterAlreadyDownloadedPages>,
        --stop-after-already-downloaded-pages <stopAfterAlreadyDownloadedPages>
            Harvesting stops after the nth page which is already fully
            downloaded (default is not to stop when this option is missing)

            This option may occur a maximum of 1 times


            This options value must fall in the following range: value >= 1


        -t <toPage>, --toPage <toPage>
            Harvesting stops at this page (default is last page when this
            option is missing)

            This option may occur a maximum of 1 times


            This options value must fall in the following range: value >= 1


        --threads <downloadThreadsNumber>
            Number of threads to download the images (default is 4 when this
            option is missing)

            This option may occur a maximum of 1 times


            This options value must fall in the following range: value >= 1
```