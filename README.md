# bGeigie-Drive

An Android app that communicates with the bGeigie Zen device.

## Features

**Note:**  
This is an **initial release**. More testing is required (on devices other than the Samsung S22) before the production release. Currently, it's in an **ALPHA/BETA** stage.

- Supports Android 11 and higher (API level 30).
- Data is displayed on a map using OpenStreetMap (standard or OpenTopo).
- Data is recorded and saved on the Android device.
- Tested with the bGeigie Zen (which uses BLE - Bluetooth Low Energy). It may also work with the Nano without modification.

## Usage

1. **Initial Setup**  
   - Upon first startup, the user is prompted to permit both **Bluetooth (BT)** and **Location** access.  
   - **Bluetooth** is mandatory to use the app, while **Location** is optional.

2. **Connecting to a Device**  
   - Press the **Connect** button to initiate a connection. The app will scan for nearby bGeigie devices.  
   - **Note:** Only BT devices beginning with "bGeigie" are listed to avoid interference from other devices. Click on a listed device to connect.

3. **Viewing Data**  
   - Once the first message arrives, both the map and the Info Pane at the bottom will update.  
   - For data to be valid, both **CPM** (Counts Per Minute) and **GPS** indicators must be green.  
   - The GPS indicators display the number of satellites and DOP (Dilution of Precision).  
   - Radiation values are color-coded:
     - White below 132 CPM
     - Yellow below 265 CPM
     - Orange below 529 CPM
     - Red above 529 CPM

4. **Logging Data**  
   - Press **Start Log** to begin logging.  
   - Press **Stop Log** to stop the logging.  
   - **Note:** Log files are only saved when pressing **Stop Log**. Files are saved in the `Downloads/SafeCast/DriveLogs/` folder on the Android device.

5. **Settings Menu**  
   - Access the settings by clicking the gear icon in the top-right corner.
     - **Clear Data**: Clears all data from the display and stops logging.
     - **API Key**: Allows the user to enter a SafeCast API Key (currently not fully implemented).
     - **Note:** No file is uploaded yet. Users need to manually upload log files to SafeCast via a computer.

6. **Map Controls**  
   - Click the map icon beneath the top banner to open the map menu.
     - **Scale Markers**: Makes markers visible at all zoom levels. Without it, they may disappear when zooming out.
     - **Clear Map**: Clears the overlay (but leaves background data intact).
     - The menu is dismissed by clicking the button again.

## TODO

- Refactor and restructure the code to improve readability and manageability.
- Implement a feature to upload log files to SafeCast with a single click once logging is stopped.
- Add functionality to load and display existing drive files from the Android device.
- Allow users to click on measurement markers on the map to view full details about the measurement.

## Support

- Visit [Safecast.org](https://safecast.org)
- Join our community forums
- Report issues on [GitHub](https://github.com)

## Credits

Developed and maintained by the Safecast community.
