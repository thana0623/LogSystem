"""Exception signature extraction and normalization."""

import re
import hashlib


def normalize_message(message: str) -> str:
    """Normalize error message by replacing variables with placeholders."""
    normalized = message
    normalized = re.sub(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', '<UUID>', normalized, flags=re.IGNORECASE)
    normalized = re.sub(r'\b\d{1,3}(\.\d{1,3}){3}\b', '<IP>', normalized)
    normalized = re.sub(r'\b\d{5,}\b', '<N>', normalized)
    normalized = re.sub(r"'[^']{1,50}'", "'<STR>'", normalized)
    normalized = re.sub(r'/[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)+', '<PATH>', normalized)
    return normalized


def generate_signature(exception_type: str, normalized_message: str, stacktop_3frames: str = "") -> str:
    """Generate MD5 signature for error clustering."""
    raw = f"{exception_type}\0{normalized_message}\0{stacktop_3frames}"
    return hashlib.md5(raw.encode()).hexdigest()
