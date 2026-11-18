# Online Reservation System

A Java Swing + JDBC + SQLite desktop project.

## Requirements
- JDK 17 or later
- Maven 3.8+ (or import the `pom.xml` into IntelliJ IDEA / Eclipse)

## Run
From this project folder:

```bash
mvn clean compile
mvn exec:java
```

The SQLite database file `reservation.db` is created automatically in the working directory.

## Demo login
- Username: `admin`
- Password: `admin123`

Change the demo credentials before using this project beyond a classroom demonstration.

## Sample train numbers
| Train number | Train name |
|---|---|
| 12727 | Godavari Express |
| 12760 | Charminar Express |
| 12603 | Chennai Express |
| 12723 | Telangana Express |
| 17016 | Visakha Express |

Enter a sample train number and press Enter or move out of the field to auto-fill its name.

## Features
- Login with access denied for invalid credentials
- Reservation form with train lookup
- Date and required-field validation
- Unique 10-digit PNR generation
- Booking confirmation dialog
- Fetch booking by PNR
- Confirmation prompt and cancellation

## Notes
This is an educational demo, not a live railway booking system. It does not connect to Indian Railways, check seat availability, collect payment, or issue real tickets. SQL operations use `PreparedStatement`. The demo password hashing is illustrative; production applications should use a dedicated password hashing algorithm/library such as BCrypt or Argon2.
