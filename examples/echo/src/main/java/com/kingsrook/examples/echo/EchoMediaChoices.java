/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.kingsrook.qqq.backend.core.actions.values.QCustomPossibleValueProvider;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.values.SearchPossibleValueSourceInput;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;


/*******************************************************************************
 ** Semantic possible values for the setup step's paper or label size. QQQ posts
 ** the already selected printer as the printerId dependency value; only that
 ** printer's authorized sizes are returned. No printer yet means no choices.
 *******************************************************************************/
public class EchoMediaChoices implements QCustomPossibleValueProvider<String>
{
   public static final String NAME = "echoMedia";
   static final String PRINTER_FIELD = "printerId";
   private static final int MAXIMUM = 50;



   /***************************************************************************
    ** A bare size id has no printer; exact refresh uses search with the printer.
    ***************************************************************************/
   @Override
   public QPossibleValue<String> getPossibleValue(Serializable id) throws QException
   {
      throw new QBadRequestException("Resolve paper or label sizes with the selected printer.");
   }



   /***************************************************************************
    ** Printing's authorization for the selected printer decides every choice.
    ***************************************************************************/
   @Override
   public List<QPossibleValue<String>> search(SearchPossibleValueSourceInput input) throws QException
   {
      String printer = printer(input.getOtherValues());
      if(printer == null)
      {
         return List.of();
      }
      return select(EchoConfig.current().destinations.media(printer), input.getIdList(), input.getSearchTerm(), input.getSkip(), input.getLimit());
   }



   /***************************************************************************
    ** The selected printer is a bounded identifier, never an expression.
    ***************************************************************************/
   static String printer(Map<String, Serializable> otherValues) throws QException
   {
      Serializable value = otherValues == null ? null : otherValues.get(PRINTER_FIELD);
      if(value == null || String.valueOf(value).isBlank())
      {
         return null;
      }
      String text = String.valueOf(value);
      if(text.length() > 200)
      {
         throw new QBadRequestException("Choose a valid printer first.");
      }
      return text;
   }



   /***************************************************************************
    ** Bounded exact-id or plain-text selection from one printer's choices.
    ***************************************************************************/
   static List<QPossibleValue<String>> select(List<EchoDestinations.MediaChoice> choices, List<Serializable> ids, String searchTerm, Integer skipValue, Integer limitValue) throws QException
   {
      List<QPossibleValue<String>> result = new ArrayList<>();
      if(ids != null)
      {
         if(ids.size() > MAXIMUM)
         {
            throw new QBadRequestException("Too many paper or label sizes requested.");
         }
         for(EchoDestinations.MediaChoice choice : choices)
         {
            if(ids.contains(choice.id()))
            {
               result.add(new QPossibleValue<>(choice.id(), choice.label()));
            }
         }
         return result;
      }
      String term = Objects.requireNonNullElse(searchTerm, "").toLowerCase(Locale.ROOT);
      if(term.length() > 128)
      {
         throw new QBadRequestException("Narrow the paper or label size search.");
      }
      int skip = Math.max(0, Objects.requireNonNullElse(skipValue, 0));
      int limit = Math.max(1, Math.min(MAXIMUM, Objects.requireNonNullElse(limitValue, MAXIMUM)));
      int seen = 0;
      for(EchoDestinations.MediaChoice choice : choices)
      {
         if((term.isEmpty() || choice.label().toLowerCase(Locale.ROOT).contains(term) || choice.id().toLowerCase(Locale.ROOT).contains(term))
            && seen++ >= skip && result.size() < limit)
         {
            result.add(new QPossibleValue<>(choice.id(), choice.label()));
         }
      }
      return result;
   }
}
