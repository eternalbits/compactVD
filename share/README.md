# How to install on Windows, macOS and Linux

This application requires a Java Runtime Environment equal to or greater than 9.
 Go to [`Java`](https://www.oracle.com/java/technologies/downloads/) to get the download you want.

### Install on Microsoft Windows

From the [`Releases`](https://github.com/eternalbits/compactVD/releases/) page download `compactVD-x.y-bin.zip`. You have 3 options:
* Copy to `C:\Program Files`. You have to give permission to `Continue`.
* Copy to another folder. You have to change the link in at least one place.
* Go to [`Launch4j`](http://launch4j.sourceforge.net/) and bring the file. Run as administrator. Open `CompactVD.xml`. Build wrapper.

### Install on Apple macOS

Download `zip` or `tar.gz` from the [`Releases`](https://github.com/eternalbits/compactVD/releases/). Copy `CompactVD.jar` to the `Applications` folder.

### Install on Linux

From the [`Releases`](https://github.com/eternalbits/compactVD/releases/) page download `compactVD-x.y-bin.tar.gz`. Write the following in `Terminal`:
````
sudo tar -xvf ~/Downloads/compactVD-x.y-bin.tar.gz -C /opt/
sudo cp /opt/CompactVD/io.github.eternalbits.compactvd.desktop /usr/share/applications/
````
If you don't have `sudo` access, or don't want to use `sudo`, you have the following option:
````
tar -xvf ~/Downloads/compactVD-x.y-bin.tar.gz -C ~/
nano ~/CompactVD/io.github.eternalbits.compactvd.desktop
Replace on 2 sides /opt/ with /home/<user>/
cp ~/CompactVD/io.github.eternalbits.compactvd.desktop ~/.local/share/applications/
````
