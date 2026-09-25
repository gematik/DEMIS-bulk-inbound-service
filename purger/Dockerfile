# Declare Source Digest for the Base Image
ARG SOURCE_DIGEST=sha256:257288f1dc49eb6984140869d104c0ea9ef884854eb357474a7b9ced7d01ef9e
FROM gematik1/osadl-alpine-openjdk25-jre:1.0.8@${SOURCE_DIGEST}
# Redeclare Source Digest to be used in the build context
# https://docs.docker.com/engine/reference/builder/#understand-how-arg-and-from-interact
ARG SOURCE_DIGEST

# The STOPSIGNAL instruction sets the system call signal that will be sent to the container to exit
# SIGTERM = 15 - https://de.wikipedia.org/wiki/Signal_(Unix)
STOPSIGNAL SIGTERM

# Define the exposed port or range of ports for the service
#EXPOSE 8080

HEALTHCHECK --interval=15s \
            --timeout=5s \
            --start-period=5s \
            --retries=3 \
            CMD ["/bin/pidof", "java"]

# Default USERID and GROUPID
ARG USERID=10000
ARG GROUPID=10000

COPY --chown=$USERID:$GROUPID target/bulk-inbound-purger.jar /app.jar

# Run as User (not root)
USER $USERID:$GROUPID

ENTRYPOINT ["java", "-jar", "/app.jar"]

# Git Args
ARG COMMIT_HASH
ARG VERSION

###########################
# Labels
###########################
LABEL de.gematik.vendor="gematik GmbH" \
      maintainer="software-development@gematik.de" \
      de.gematik.app="bulk-inbound-purger" \
      de.gematik.git-repo-name="https://gitlab.prod.ccs.gematik.solutions/demis/services/bulk-inbound-service" \
      de.gematik.commit-sha=$COMMIT_HASH \
      de.gematik.version=$VERSION \
      de.gematik.source.digest=$SOURCE_DIGEST
