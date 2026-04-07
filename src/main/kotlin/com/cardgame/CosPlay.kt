package com.cardgame

import org.cosplay.CPKeyboardKey
import org.cosplay.CPShader
import scala.jdk.CollectionConverters

@Suppress("UNCHECKED_CAST")
fun <T> scalaSeqOf(vararg items: T): scala.collection.immutable.Seq<T> {
    val javaList = items.toList()
    val scalaIterable = CollectionConverters.IterableHasAsScala(javaList as Iterable<T>).asScala()
    return scalaIterable.toSeq()
}

fun emptyScalaSeq(): scala.collection.immutable.Seq<CPShader> = scalaSeqOf<CPShader>()

fun emptyStringSet(): scala.collection.immutable.Set<String> =
    scala.collection.immutable.HashSet<String>()

fun kbKey(name: String): CPKeyboardKey = CPKeyboardKey.valueOf(name)
