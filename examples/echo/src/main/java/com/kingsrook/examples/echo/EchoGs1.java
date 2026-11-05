/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Bounded GS1-128 element-string profile. Original scan bytes remain unchanged.
 ** Fixed-length identifiers need no separator; variable fields end only at GS
 ** or end of input. Never guess an AI boundary from digits inside a value.
 ** Source: GS1 General Specifications, element strings and predefined lengths.
 *******************************************************************************/
final class EchoGs1
{
   private static final char SEPARATOR = 29;
   private static final Map<String, Integer> FIXED = Map.ofEntries(
      Map.entry("00", 18), Map.entry("01", 14), Map.entry("02", 14),
      Map.entry("11", 6), Map.entry("12", 6), Map.entry("13", 6),
      Map.entry("15", 6), Map.entry("16", 6), Map.entry("17", 6), Map.entry("20", 2));
   private static final Map<String, Integer> VARIABLE = Map.of("10", 20, "21", 20, "30", 8, "37", 8);



   /***************************************************************************
    ** Utility class.
    ***************************************************************************/
   private EchoGs1()
   {
   }



   /***************************************************************************
    ** Convert the supported AIs to canonical encoder input and printed HRI.
    ** The renderer independently validates canonical GS1 before producing bytes.
    ***************************************************************************/
   static Value parse(String value) throws QException
   {
      if(value == null || value.isEmpty() || value.length() > 48
         || value.chars().anyMatch(c -> (c < 32 && c != SEPARATOR) || c > 126 || c == '[' || c == ']' || c == '\\'))
      {
         throw refusal();
      }
      StringBuilder canonical = new StringBuilder();
      StringBuilder readable = new StringBuilder();
      int offset = 0;
      while(offset < value.length())
      {
         if(offset + 2 > value.length())
         {
            throw refusal();
         }
         String ai = value.substring(offset, offset + 2);
         Integer fixed = FIXED.get(ai);
         Integer maximum = VARIABLE.get(ai);
         if(fixed == null && maximum == null)
         {
            throw new QException("This GS1 application identifier is not in the qualified Echo profile. No print was requested.");
         }
         offset += 2;
         int end = fixed == null ? value.indexOf(SEPARATOR, offset) : offset + fixed;
         if(end < 0)
         {
            end = value.length();
         }
         if(end > value.length() || end == offset || (maximum != null && end - offset > maximum))
         {
            throw refusal();
         }
         String data = value.substring(offset, end);
         boolean numeric = fixed != null || "30".equals(ai) || "37".equals(ai);
         if(data.indexOf(SEPARATOR) >= 0 || (numeric && !data.chars().allMatch(c -> c >= '0' && c <= '9')))
         {
            throw refusal();
         }
         canonical.append('[').append(ai).append(']').append(data);
         readable.append('(').append(ai).append(')').append(data);
         offset = end;
         if(offset < value.length() && value.charAt(offset) == SEPARATOR)
         {
            // Refuse redundant/trailing separators instead of silently changing the scan. //
            if(fixed != null || ++offset == value.length())
            {
               throw refusal();
            }
         }
      }
      return new Value(canonical.toString(), readable.toString());
   }



   /***************************************************************************
    ** A safe refusal carries no barcode contents.
    ***************************************************************************/
   private static QException refusal()
   {
      return new QException("The GS1-128 payload is outside the qualified profile or has invalid field boundaries. No print was requested.");
   }



   /***************************************************************************
    ** Derived output, never a replacement for the original persisted barcode.
    ***************************************************************************/
   record Value(String canonical, String readable)
   {
   }
}
