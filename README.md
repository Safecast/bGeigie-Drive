# bGeigie-Drive
An android app which communicates with the bGeigie Zen device.
## Features
**NOTE: This is currently an initial release and more testing (on other devices than a single Samsung S22) is needed before production release, i.e. this is currently a ALFA/BETA version!**
- Supports android 14 and higher (API level 34).
- Data is displays on a map, OpenStreetMap (standard or OpenTopo).
- Data is recorded and saved on the android device.
## Usage
- At first startup the user is asked to permit the use of both **BT** and **Location**. User MUST consent to **BT** to use the app but **Location** is optional.
- Connect to a bGeigieZen (possibly also the Nano?) by pressing the **Connect**-button. The app will now look for nearby BGeigie devices and list them. Note: Only BT devices beginning with "bGeigie" are listed (to avoid numerous other devices). Click on a listed device to connect to it.
- The map and the Info Pane at the bottom are updated once the first message arrives. Note: Both the **CPM** and the **GPS** indicators must be green in order to receive useful data. The numbers after the GPS are the number of satellites followed by DOP (Dilution Of Precision). The radiation values will be white below 132 CPM, yellow below 265, orange below 529 and finally red above that.
- By clicking on the button **Start Log** logging will be initiated and by pressing **Stop Log** the logging will be stopped. Note: The log file is only written when pressing **Stop Log**. The file on the android device will be similar to the ones created and stored in the bGeigie.
- By clicking on the gear symbol at the top right a settings menu is invoked. The button **Clear Data** WILL clear all data and also remove the from the display. Note: This will also kill logging as the log file is written when stopping the log, see above. The button **API Key** will allow the user to enter his SafeCast API Key. Note: This feature is not yet fully implemented and no file is currently uploaded. Instead, the user must connect his device to a computer, access the directory (Downloads/SafeCast/DriveLogs/) and upload a file to SafeCast from there.
- By clicking on the map symbol, to the right under the top banner, a menu is invoked. A number of display features can be controlled from here. When the **Scale Markers** option is selected the markers will be visible at any scale, otherwise the will be too small to see when zooming out. The **Clear Map** button will just clear the overlay, i.e. tha data in the background is NOT touched. The menu is dismissed when clicking on the button again.

## TODO
- Add feature to upload drive file to SafeCast with a single click once a log is stopped.
- Add feature to load existing drive files from the android device and display them.
- Add feature to click on a measurement marker to see full details regarding that measurement.

## Support

- Visit [Safecast.org](https://safecast.org)
- Join our community forums
- Report issues on GitHub

## Credits

Developed and maintained by the Safecast community.
