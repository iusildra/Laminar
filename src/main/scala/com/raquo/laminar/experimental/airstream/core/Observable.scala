package com.raquo.laminar.experimental.airstream.core

import com.raquo.laminar.experimental.airstream.ownership.Owner
import com.raquo.laminar.experimental.airstream.util.GlobalCounter
import org.scalajs.dom

import scala.scalajs.js

/** This trait represents a reactive value that can be subscribed to. */
trait Observable[+A] {

  // @TODO remove id? Why did I even add it?
  val id: Int = Observable.nextId()

  protected[airstream] val topoRank: Int

  /** Note: Observer can be added more than once to an Observable.
    * If so, it will observe each event as many times as it was added.
    */
  protected[this] lazy val externalObservers: js.Array[Observer[A]] = js.Array()

  /** Note: This is enforced to be a Set outside of the type system #performance */
  protected[this] val internalObservers: js.Array[InternalObserver[A]] = js.Array()

  def foreach(onNext: A => Unit)(implicit subscriptionOwner: Owner): Subscription = {
    val observer = Observer(onNext)
    addObserver(observer)(subscriptionOwner)
  }

  // @TODO explain the difference between child observers and external observers
  /** And an external observer */
  def addObserver(observer: Observer[A])(implicit subscriptionOwner: Owner): Subscription = {
    val subscription = Subscription(observer, this, subscriptionOwner)
    externalObservers.push(observer)
    dom.console.log(s"Adding subscription: $subscription")
    subscription
  }

  /** Note: To completely disconnect an Observer from this Observable,
    * you need to remove it as many times as you added it to this Observable.
    *
    * @return whether observer was removed (`false` if it wasn't subscribed to this observable)
    */
  def removeObserver(observer: Observer[A]): Boolean = {
    val index = externalObservers.indexOf(observer)
    val shouldRemove = index != -1
    if (shouldRemove) {
      externalObservers.splice(index, deleteCount = 1)
    }
    shouldRemove
  }

  // @TODO Why does simple "protected" not work? Specialization?

  /** Child stream calls this to declare that it was started */
  protected[airstream] def addInternalObserver(observer: InternalObserver[A]): Unit = {
    internalObservers.push(observer)
  }

  /** */
  protected[airstream] def removeInternalObserver(observer: InternalObserver[A]): Boolean = {
    val index = internalObservers.indexOf(observer)
    val shouldRemove = index != -1
    if (shouldRemove) {
      internalObservers.splice(index, deleteCount = 1)
    }
    shouldRemove
  }

  // @TODO These two methods need updated description because I'm getting them to work for non-lazy observables as well

  /** This method is fired when this stream gets its first observer,
    * directly or indirectly (via child streams).
    *
    * Before this method is called:
    * - 1) This stream has no direct observers
    * - 2) None of the streams that depend on this stream have observers
    * - 3) Item (2) above is true for all streams depend on this stream
    *
    * This method is called when any of these conditions become false (often together at the same time)
    */
  protected[this] def onStart(): Unit = ()

  /** This method is fired when this stream loses its last observer,
    * including indirect ones (observers of child streams)
    *
    * Before this method is called:
    * - 1) This stream has observers, or
    * - 2) At least one stream that depends on this stream has observers, or
    * - 3) Item (2) above is true for all streams depend on this stream
    *
    * This method is called when any of these conditions become false
    */
  protected[this] def onStop(): Unit = ()

  // @TODO[API] It is rather curious/unintuitive that firing external observers first seems to make more sense. Think about it some more.
  // @TODO[API] Should probably use while-loops here to support the case of adding observers on the fly (will fix simpler cases of https://github.com/raquo/laminar/issues/11)
  protected[this] def fire(nextValue: A, transaction: Transaction): Unit = {
    externalObservers.foreach(_.onNext(nextValue))
    internalObservers.foreach(_.onNext(nextValue, transaction))
  }
}

object Observable extends GlobalCounter
