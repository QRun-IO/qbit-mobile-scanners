/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.util.List;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/*******************************************************************************
 ** Synthetic GS1 examples; no customer label values are retained as fixtures.
 *******************************************************************************/
class EchoGs1Test
{
   /***************************************************************************
    ** Tomorrow's label structure: GTIN, production date and terminal serial.
    ***************************************************************************/
   @Test
   void gtinProductionDateAndSerialPreserveZeros() throws Exception
   {
      EchoGs1.Value parsed = EchoGs1.parse("01095011015300031120101421TEST000123");
      assertEquals("[01]09501101530003[11]201014[21]TEST000123", parsed.canonical());
      assertEquals("(01)09501101530003(11)201014(21)TEST000123", parsed.readable());
   }



   /***************************************************************************
    ** A numeric sequence in a variable value never invents a new field boundary.
    ***************************************************************************/
   @Test
   void variableFieldsUseOnlyActualSeparators() throws Exception
   {
      assertEquals("[10]LOT21ABC[21]00007", EchoGs1.parse("10LOT21ABC\u001d2100007").canonical());
      assertEquals("[10]LOT21ABC2100007", EchoGs1.parse("10LOT21ABC2100007").canonical());
   }



   /***************************************************************************
    ** Malformed, unsupported and escape-like inputs refuse before generation.
    ***************************************************************************/
   @Test
   void refusesUnsupportedOrAmbiguousInputs()
   {
      for(String input : List.of("", "01095011", "010950110153000X", "99UNKNOWN", "21", "21A\u001d", "21A\u001d\u001d21B",
         "0109501101530003\u001d21A", "21[01]123", "21A\\<FNC1>B", "21A\nB", "21é", "21" + "A".repeat(21)))
      {
         assertThrows(QException.class, () -> EchoGs1.parse(input));
      }
   }
}
