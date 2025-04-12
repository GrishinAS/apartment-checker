# 🏢 Apartment Notifier Bot

A Spring Boot application that periodically checks for newly listed apartments on a rental company's website and sends notifications to subscribed users via Telegram.

---

## 🚀 Features

- Retrieves new apartment listings at a configurable interval
- Sends Telegram messages to users who subscribed to updates
- Uses an H2 in-memory database for storing subscription and apartment data

---

## 📥 Usage

To start receiving notifications about new apartment listings:

1. **Send `/start` to the bot**  
   This will initiate the subscription process.

2. **Follow the prompts**  
   You'll be guided to set up a filter for the types of apartments you're interested in (e.g., location, price range, size, etc.).

3. Once you're subscribed, the bot will periodically check for new listings and notify you if they match your criteria.

### Additional Commands

- `/unsubscribe` — Stop receiving notifications about new apartments.
- `/getCurrentAvailable` — Get a list of all currently available apartments that match your filter.

> 💡 You can re-subscribe at any time by sending `/start` again and setting up a new filter.
---

## ⚙️ Configuration

### Required Environment Variables

| Variable             | Description                        |
|----------------------|------------------------------------|
| `TELEGRAM_BOT_TOKEN` | Token of your Telegram bot         |
| `SQL_DB_USERNAME`    | Username for the local H2 database |
| `SQL_DB_PASSWORD`    | Password for the local H2 database |

You can provide these environment variables in your system, or create a `.env` file and use a tool like [dotenv](https://github.com/cdimascio/dotenv-spring-boot) or your IDE to load them during development.

### Custom Properties

Configure the scraping interval in `application.yml` or `application.properties`:

```yaml
apartment-check:
  interval: 10  # In minutes, 30 is the minimun
 ```

## 🛠️ How It Works

1. The app synchronize existing apps on startup and starts a scheduled task based on the interval specified in the config.  
2. It checks the target apartment rental website for new listings.  
3. Compares with previously fetched listings to detect new ones.  
4. Sends a Telegram message with the apartment info to all subscribed users.  

---

## 🧪 Running Locally

```bash
git clone https://github.com/GrishinAS/apartment-checker.git
cd apartment-checker

# Set environment variables before running
export TELEGRAM_BOT_TOKEN=your_telegram_token
export SQL_DB_USERNAME=sa
export SQL_DB_PASSWORD=password

chmod +x gradlew
./gradlew bootRun
```

## 🧾 API (Optional)

Methods mostly used for testing:

- `/sync` — Runs actualization of current database state without triggering notifications
- `/forceScheduledCheck` — Runs scheduled check ahead of time with notifications triggering



