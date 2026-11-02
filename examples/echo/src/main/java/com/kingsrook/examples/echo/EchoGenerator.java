/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** The existing generation service retains output and uses its configured native
 ** printing bridge. Queue acceptance is not a claim of physical paper output.
 *******************************************************************************/
@FunctionalInterface
public interface EchoGenerator
{
   /***************************************************************************
    ** Keep the same key for retries; an unavailable result never means absence.
    ***************************************************************************/
   String accept(EchoDestinations.Plan plan, String scanId, String requestKey, EchoDestinations.Destination destination) throws QException;



   /***************************************************************************
    ** Read-only owner evidence may recover an accepted job after a process crash.
    ** Missing/unavailable evidence never authorizes another render or enqueue.
    ***************************************************************************/
   default String lookupAccepted(EchoDestinations.Plan plan, String scanId, String requestKey, EchoDestinations.Destination destination) throws QException
   {
      throw new QException("Print acceptance requires owner reconciliation. Leave Echo to stop scanning; do not create a replacement print.");
   }
}
