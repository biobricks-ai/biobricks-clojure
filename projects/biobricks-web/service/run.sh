#!/usr/bin/bash

set -eu

export PATH=/home/admin/bin:/home/linuxbrew/.linuxbrew/bin:$PATH:/nix/var/nix/profiles/default/bin/

HYPERFIDDLE_ELECTRIC_APP_VERSION=$(cat electric-version)
echo Version $HYPERFIDDLE_ELECTRIC_APP_VERSION
nix develop -c java -server -Xms1536m -Xmx1536m -XX:GCTimeLimit=50 \
  -DHYPERFIDDLE_ELECTRIC_SERVER_VERSION=$HYPERFIDDLE_ELECTRIC_APP_VERSION \
  -Xss2m \
  --add-opens=java.base/java.nio=ALL-UNNAMED \
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
  -jar biobricks-web-"$HYPERFIDDLE_ELECTRIC_APP_VERSION".jar
