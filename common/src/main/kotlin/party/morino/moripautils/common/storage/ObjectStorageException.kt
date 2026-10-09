/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package party.morino.moripautils.common.storage

import java.io.IOException

/**
 * オブジェクトストレージの操作に失敗したことを表す例外
 *
 * SDK 固有の例外を機能側へ漏らさないよう、[ObjectStorage] の実装はこの例外に包み直して投げる。
 *
 * @param message 失敗の内容
 * @param cause 元になった例外
 */
class ObjectStorageException(message: String, cause: Throwable? = null) : IOException(message, cause)
