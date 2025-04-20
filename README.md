# 🏢 Apartment Notifier Bot

A Spring Boot application that periodically checks for newly listed apartments on a rental company's website and sends notifications to subscribed users via Telegram.

---

## 🚀 Features

- Retrieves new apartment listings at a configurable interval
- Sends Telegram messages to users who subscribed to updates
- Uses PostgreSQL for storing subscription and apartment data

---

## 🛠️ How It Works

1. The app synchronize existing apps on startup and starts a scheduled task based on the interval specified in the config.
2. It checks the target apartment rental website for new listings.
3. Compares with previously fetched listings to detect new ones.
4. Sends a Telegram message with the apartment info to all subscribed users.

---

## 📥 Usage

To start receiving notifications about new apartment listings:

1. **Send `/start` to the bot**  
   This will initiate the subscription process.

2. **Follow the prompts**  
   You'll be guided to set up a filter for the types of apartments you're interested in (e.g., location, price range, size, etc.).

3. Once you're subscribed, the bot will periodically check for new listings and notify you if they match your criteria.

## ⚙️ Configuration

### Required Environment Variables

| Variable             | Description                        |
|----------------------|------------------------------------|
| `TELEGRAM_BOT_TOKEN` | Token of your Telegram bot         |
| `SQL_DB_USERNAME`    | Username for the local H2 database |
| `SQL_DB_PASSWORD`    | Password for the local H2 database |

You can provide these environment variables in your system, or create a `.env` file and use a tool like [dotenv](https://github.com/cdimascio/dotenv-spring-boot) or your IDE to load them during development.

### Custom Properties

Configure the scraping interval in `application.yml`:

```yaml
apartment-check:
  interval: 120  # In minutes
 ```

## 🧪 Running Locally

```bash
git clone https://github.com/GrishinAS/apartment-checker.git
cd apartment-checker

# Set environment variables before running
export TELEGRAM_BOT_TOKEN=your_telegram_token
export SQL_DB_USERNAME=sa
export SQL_DB_PASSWORD=password

./gradlew bootRun
```

You can move start.sh and stop.sh scripts one level above the project folder and use them to start/stop the app and store credentials

## 🧾 API (Optional)

Methods mostly used for testing:

- `/sync` — Runs actualization of current database state without triggering notifications
- `/forceScheduledCheck` — Runs scheduled check ahead of time with notifications triggering


## TODO 
- Add multiple communities subscription support
- Amenities filter fix
- Bedrooms and bathroom filters
