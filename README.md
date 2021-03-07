PerseveranceHarvester
=====================
WHat is it?
-----------
**PerseveranceHarvester** is a Mars rover **Perseverance** raw images harvester. It is a **Java standalone program** that downloads the raw images taken by the Mars rover **Perseverance** that are available on the NASA website at the following URL: <https://mars.nasa.gov/mars2020/multimedia/raw-images/>

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
                [ {-t | --toPage} <toPage> ]

OPTIONS
        -d <saveRootDirectory>, --dir <saveRootDirectory>
            Root directory in which the images are saved

        -f <fromPage>, --fromPage <fromPage>
            Harvesting starts from this page

        --force
            Force harvesting already downloaded images

        -h, --help
            Display help information

        -t <toPage>, --toPage <toPage>
            Harvesting stops at this page
```