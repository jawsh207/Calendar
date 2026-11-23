#!/usr/bin/env bash
# Makes Calendar's two signing keys and stores them as the repository's GitHub Actions secrets.
#
#   scripts/make-signing-keys.sh [folder]
#
# Run it once, on your own computer, with keytool (any JDK) and the GitHub CLI (`gh auth login`).
# The keystores are written to [folder] (default ~/keys/calendar), outside the repository, and
# are never committed. Back that folder and the passwords up: an app signed with a lost release
# key can't be updated, only uninstalled and installed again.
#
#   calendar-dev.jks  alias calendar-dev  -> DEV_KEYSTORE_BASE64, DEV_KEYSTORE_PASSWORD
#   calendar.jks      alias calendar      -> RELEASE_KEYSTORE_BASE64, RELEASE_KEYSTORE_PASSWORD
#
# PKCS12 keystores have one password for the store and the key, which is what CI expects.
# Existing keystores are kept and only uploaded again, so running it twice is safe.
set -euo pipefail

REPO=jawsh207/Calendar
DIR=${1:-"$HOME/keys/calendar"}
mkdir -p "$DIR"
chmod 700 "$DIR"

command -v keytool >/dev/null || { echo "keytool not found: install a JDK first." >&2; exit 1; }
command -v gh >/dev/null || { echo "gh not found: install the GitHub CLI and run 'gh auth login'." >&2; exit 1; }

# The base64 tool takes different options on Linux and macOS; this works on both.
encode() { base64 < "$1" | tr -d '\n'; }

make_key() {
    local kind=$1 alias=$2 file="$DIR/$2.jks" pass again
    echo
    echo "== $kind key ($alias) =="
    read -rsp "Password for $alias (at least 6 characters): " pass; echo
    if [ ! -f "$file" ]; then
        read -rsp "Same password again: " again; echo
        [ "$pass" = "$again" ] || { echo "The passwords don't match." >&2; exit 1; }
        [ ${#pass} -ge 6 ] || { echo "keytool needs at least 6 characters." >&2; exit 1; }
        keytool -genkeypair -v -storetype PKCS12 -keystore "$file" -alias "$alias" \
            -keyalg RSA -keysize 4096 -validity 10000 \
            -storepass "$pass" -keypass "$pass" \
            -dname "CN=jawsh207, O=Calendar" >/dev/null
        chmod 600 "$file"
        echo "Made $file"
    else
        echo "Keeping the existing $file"
    fi
    # Checks the password before uploading, so CI never gets a pair that doesn't open.
    keytool -list -keystore "$file" -storepass "$pass" -alias "$alias" >/dev/null \
        || { echo "That password doesn't open $file." >&2; exit 1; }
    local prefix=$3
    encode "$file" | gh secret set "${prefix}_KEYSTORE_BASE64" -R "$REPO"
    printf '%s' "$pass" | gh secret set "${prefix}_KEYSTORE_PASSWORD" -R "$REPO"
    echo "Set ${prefix}_KEYSTORE_BASE64 and ${prefix}_KEYSTORE_PASSWORD on $REPO"
    echo "SHA-256: $(keytool -list -v -keystore "$file" -storepass "$pass" -alias "$alias" | grep -m1 'SHA256:' | sed 's/.*SHA256: //')"
}

make_key "Test build" calendar-dev DEV
make_key "Release" calendar RELEASE

echo
echo "Done. Back up $DIR and both passwords somewhere safe."
echo "Every build from now on is signed with these keys; each run's page shows the certificate."
