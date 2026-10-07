# DepotCharge

DepotCharge is a native Android starting point for bus-depot electric-vehicle
charging schedules. The initial plan is seeded from the `Database1` sheet in
the supplied `EB App.xlsm` workbook. It retains the workbook's bus, charger,
start time, end time, and charging-duration records; it does not execute or
carry over the workbook's VBA macros.

## Included

- Overview of the imported plan, fleet, chargers, and next sessions
- Searchable schedule with upcoming, charging, completed, and issue filters
- Charger-by-charger availability view
- New session entry with bus/charger range checks and overlap prevention
- Local on-device storage for added and removed sessions
- Explicit review warning for the two charger overlaps already present in the
  source workbook
- Times after midnight shown with a `+1` day marker

The source workbook contains 160 sessions, buses 1–101, and chargers 1–20.
The sheet has no service date, so the app treats its rows as a reusable daily
schedule template and labels it with the device's current date.

## Build

Open this folder in Android Studio and sync the Gradle project. The project
targets Android 8.0 (API 26) and newer. Run the `app` configuration on an
emulator or connected device.
