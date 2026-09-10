# JellyPlay web shell (wasmJs) as an OCI image.
#
# The build context is the apps/web MODULE DIRECTORY, not a staging copy:
# .dockerignore prunes everything except the production bundle, so a build is
#   ./gradlew :apps:web:wasmJsBrowserDistribution
#   docker build apps/web
# CI (kmp-release.yml web-image job) unpacks the web-bundle artifact into the
# same path instead of rebuilding, so release assets and the container image
# are byte-identical output from one wasmJsBrowserDistribution run.
#
# webapp.js is UNHASHED (pinned in apps/web/build.gradle.kts), the .wasm
# assets are hashed by webpack — nginx.conf picks the safe caching posture
# for that mix (etag revalidation, no immutable headers).
FROM nginx:1.29-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY build/dist/wasmJs/productionExecutable/ /usr/share/nginx/html/

EXPOSE 80
