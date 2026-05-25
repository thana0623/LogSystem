"""LogSystem Analyzer — error clustering and statistics (Phase 2)."""

import os
import time
import schedule
from dotenv import load_dotenv

load_dotenv()


def run_clustering():
    """Run error clustering job."""
    # TODO: Phase 2 implementation
    pass


def run_stats():
    """Run statistics aggregation job."""
    # TODO: Phase 2 implementation
    pass


def main():
    interval = int(os.getenv("ANALYZER_INTERVAL_MINUTES", "5"))
    schedule.every(interval).minutes.do(run_clustering)
    schedule.every(interval).minutes.do(run_stats)

    print(f"LogSys Analyzer started (interval={interval}min)")
    while True:
        schedule.run_pending()
        time.sleep(1)


if __name__ == "__main__":
    main()
