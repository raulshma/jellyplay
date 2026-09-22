package com.raulshma.jellyplay.core.network.github

import java.io.IOException

/**
 * A fail-closed violation of the [GitHubRepoAllowList] pin: a URL involved
 * in the update check — the final post-redirect endpoint URL, the release
 * `html_url`, or an asset `browser_download_url` — pointed outside the
 * compiled-in GitHub identity (a moved repo, a hostile redirect, or a
 * poisoned cached [com.raulshma.jellyplay.core.model.AppUpdateInfo]).
 *
 * Callers surface this as a Result failure WITHOUT the
 * `ApiException.fromNetwork` ladder on purpose: that classification marks
 * IOExceptions retryable, and a takeover/tamper signal must reach the user,
 * never be retried.
 */
class UpdateSecurityException(message: String) : IOException(message)
