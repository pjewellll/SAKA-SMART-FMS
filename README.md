# 🌾 SAKA-SMART — Farm Management System

**Java 21 desktop starter project for VS Code** · Java Swing UI · Local persistent demo or PostgreSQL database

This is a **working starter/MVP**, not a finished production release. It follows the user's uploaded SAKA-SMART mockup layout (dark-green sidebar, dashboard cards, tables and farm management screens), while using a simplified Java Swing implementation to run on a Core i3 laptop **without requiring JavaFX or Maven**.

## 🚀 Fastest way to run on Windows

1. **Extract the entire `SAKA_SMART_Java_Desktop` ZIP** to a normal folder, e.g. Desktop.
2. Open the folder in **Visual Studio Code → File → Open Folder**.
3. **Double-click `run-demo.bat`** from File Explorer (or open VS Code Terminal and type `./run-demo.bat`).
4. Wait for Java 21 to compile and the app window to open. **First run creates `data/saka-smart-demo.dat`; later runs keep your local changes.**
5. Sign in as a farm user or a system administrator:

   | Login tab | Username | Initial password |
   |---|---|---|
   | Farmer / Staff | `farmer` | `Farmer@123` |
   | System Admin | `admin` | `Admin@123` |

   **Demo passwords are only for learning/testing. Change them in Settings or Admin Users before entering real information.**

**No extra libraries are needed for demo mode.** You already have Java 21 installed (`java` and `javac` working). Windows may ask if you trust the local `.bat` file; you can open it in Notepad first to inspect the commands. This project does **not** need Python, Maven, Spring Boot, a web server or a browser.

### Running from VS Code

- Install **Extension Pack for Java** (Microsoft) if you have not done so.
- Open `src/com/sakasmart/Main.java` and use **Run Java** (the `.vscode/launch.json` demo profile sets `SAKA_MODE=demo`).
- The simplest reliable option is still `run-demo.bat` in VS Code Terminal or File Explorer.
- When using VS Code, open the **folder**, not an individual `.java` file.

## 🐘 Connecting to your PostgreSQL database (for the class requirement)

**Do this after confirming the demo runs. PostgreSQL requires a JDBC driver JAR and a database.**

1. In **pgAdmin 4**, connect to your normal PostgreSQL server. Expand `Databases` and confirm whether `sakasmart` already exists.
2. If it doesn't exist, select the `postgres` maintenance database, open **Query Tool**, and execute:

   ```sql
   CREATE DATABASE sakasmart;
   ```

   This SQL is also in `database/create_database.sql`. Do **not** run it if the database already exists.
3. Download the official PostgreSQL JDBC driver (pgJDBC) from **https://jdbc.postgresql.org/download/** and place the downloaded `postgresql-42.x.x.jar` file in the project's `lib/` directory. Don't rename the Java source files.
4. Double-click `run-postgres.bat`. It uses these connection settings by default:

   ```text
   Database: sakasmart
   Host: localhost
   Port: 5432
   Username: postgres
   Password: asked for in the terminal (input visible)
   ```

   You can override the connection through temporary environment variables `SAKA_DB_URL`, `SAKA_DB_USER`, and `SAKA_DB_PASSWORD` in the terminal. **Do not put passwords in the project or GitHub.**
5. The app initializes its PostgreSQL tables on first run and creates the same two starter accounts, but **no example farm transactions**. Add your own records using the application.
6. In the top bar, verify the indicator reads **“PostgreSQL connected”**. Otherwise, you are still using local demo storage.

**Important:** Local demo and PostgreSQL are separate datasets. Switching to PostgreSQL does not import the demo records. PostgreSQL needs to be running and reachable when you use PostgreSQL mode. The offline demo feature does not mean the PostgreSQL mode works offline or includes automatic cloud synchronization.

## ✅ Functional features in this starter

| Area | Implemented |
|---|---|
| User / Admin login | Working passwords with salted PBKDF2 hashes, active-account checking, admin/user login tabs |
| Registration | Create account with `VIEWER` role, inactive pending admin activation |
| Farmer dashboard | Real summary from entered inventory, upcoming tasks, monthly income and stock alerts |
| Inventory | Farm supplies / harvested product tabs, add/edit/delete, stock in/out, search, CSV export, low-stock and expiry notifications |
| Calendar | Crop and livestock schedules, monthly date view, add/edit/delete, mark complete |
| Production tracking | Add/edit/delete output logs and view target attainment charts |
| Accounting | Add/edit/delete manual income/expense entries, net balance, category chart and CSV export |
| Marketplace / Sales | Product availability, record sale, **atomic stock deduction + corresponding income record**, sales history and CSV export |
| Learning Center | Search/read articles; admin can add/edit/delete content |
| Settings | Farm profile, account password change, user role management for admin |
| Admin Panel | Users (activate/deactivate, role changes, reset password), farm profile, logs, learning articles and CSV archive |
| Local Demo mode | Saved to `data/saka-smart-demo.dat` using Java object serialization |
| PostgreSQL mode | JDBC CRUD stored in real PostgreSQL SQL tables |

## 🚧 Not implemented yet (planned later)

- Exact pixel-perfect recreation of every uploaded high-fidelity mockup image.
- Fully isolated multi-farm accounts and tenant administration (the MVP has **one farm**).
- Real-time weather API, cloud backup/sync, background push reminders.
- PDF/Excel reports or full PDF printing (CSV export is implemented).
- Complete invoices, customer balances, purchases, procurement, livestock health case management.
- Full database restoration from the CSV export; use pgAdmin's native backup and restore tools for PostgreSQL.
- Multi-user conflict management and enterprise-grade server-side permissions; current roles are enforced primarily at the desktop UI level. **Do not deploy on an untrusted network as-is.**
- Formal automated tests and production packaging/installer.

## 📁 Project layout

```text
SAKA_SMART_Java_Desktop/
├── src/com/sakasmart/
│   ├── Main.java                # Swing interface and user actions
│   ├── Schema.java              # Database table / form field definitions
│   ├── Storage.java             # Shared data access operations
│   ├── DemoStorage.java         # Local persisted demo data
│   ├── PostgresStorage.java     # PostgreSQL JDBC backend
│   └── Security.java            # Password hashing
├── lib/                         # Put pgJDBC JAR here for PostgreSQL
├── database/create_database.sql
├── .vscode/launch.json
├── .vscode/settings.json
├── run-demo.bat
├── run-postgres.bat
└── README.md
```

## 🔐 Notes on project safety and correctness

- **Do not enter real personal information** using initial demo credentials.
- Change initial admin/farmer passwords before any classroom deployment on a shared database.
- PostgreSQL operations use parameterized SQL with a table/column whitelist; the sale recording transaction is atomic.
- The export intentionally excludes password hashes.
- Never push `data/` or PostgreSQL passwords to a public GitHub repository.
- Confirm stock, finance, and permissions with tests before using the system for actual farm decisions.

## 💚 Suggested next development step

Ask: **“Jewell's SAKA-SMART demo is running; help us connect this folder to PostgreSQL and start our first inventory test.”**

We can then improve the visual design with your screenshot references, set up GitHub branches for the three members, normalize the ERD, and build the more advanced integrations one at a time.
