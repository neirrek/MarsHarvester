# PerseveranceHarvester
**PerseveranceHarvester** is a Mars rover **Perseverance** raw images harvester. It is a **Java standalone program** that downloads the raw images taken by the Mars rover **Perseverance** that are available on the NASA website at the following URL: <https://mars.nasa.gov/mars2020/multimedia/raw-images/>

### Usage
```
NAME
        perseverance-harvester - Perseverance raw images harvester command

SYNOPSIS
        perseverance-harvester [ {-f | --fromPage} <fromPage> ] [ --force ]
                [ {-h | --help} ] [ {-t | --toPage} <toPage> ]

OPTIONS
        -f <fromPage>, --fromPage <fromPage>
            Harvesting starts from this page

        --force
            Force harvesting already downloaded images

        -h, --help
            Display help information

        -t <toPage>, --toPage <toPage>
            Harvesting stops at this page
```