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
 ** Semantic possible values for the setup step's Document: true INTEGER ids,
 ** Generation's template ids. QQQ posts the already selected printer and paper
 ** or label size as dependency values; only published Echo-compatible documents
 ** the actor may use for that pair are returned.
 *******************************************************************************/
public class EchoDocumentChoices implements QCustomPossibleValueProvider<Integer>
{
   public static final String NAME = "echoDocument";
   static final String MEDIA_FIELD = "mediaId";
   private static final int MAXIMUM = 50;



   /***************************************************************************
    ** A bare document id has no printer or size; exact refresh uses search.
    ***************************************************************************/
   @Override
   public QPossibleValue<Integer> getPossibleValue(Serializable id) throws QException
   {
      throw new QBadRequestException("Resolve documents with the selected printer and paper or label size.");
   }



   /***************************************************************************
    ** Printing and Generation authorization decide every choice.
    ***************************************************************************/
   @Override
   public List<QPossibleValue<Integer>> search(SearchPossibleValueSourceInput input) throws QException
   {
      String printer = EchoMediaChoices.printer(input.getOtherValues());
      String media = media(input.getOtherValues());
      if(printer == null || media == null)
      {
         return List.of();
      }
      return select(EchoConfig.current().destinations.documents(printer, media), input.getIdList(), input.getSearchTerm(), input.getSkip(), input.getLimit());
   }



   /***************************************************************************
    ** Bounded exact-id (strict integers) or plain-text selection.
    ***************************************************************************/
   static List<QPossibleValue<Integer>> select(List<EchoDestinations.DocumentChoice> choices, List<Serializable> ids, String searchTerm, Integer skipValue, Integer limitValue) throws QException
   {
      List<QPossibleValue<Integer>> result = new ArrayList<>();
      if(ids != null)
      {
         if(ids.size() > MAXIMUM)
         {
            throw new QBadRequestException("Too many documents requested.");
         }
         List<Integer> wanted = new ArrayList<>();
         for(Serializable id : ids)
         {
            wanted.add(EchoSteps.documentId(id));
         }
         for(EchoDestinations.DocumentChoice choice : choices)
         {
            if(wanted.contains(choice.id()))
            {
               result.add(new QPossibleValue<>(choice.id(), choice.label()));
            }
         }
         return result;
      }
      String term = Objects.requireNonNullElse(searchTerm, "").toLowerCase(Locale.ROOT);
      if(term.length() > 128)
      {
         throw new QBadRequestException("Narrow the document search.");
      }
      int skip = Math.max(0, Objects.requireNonNullElse(skipValue, 0));
      int limit = Math.max(1, Math.min(MAXIMUM, Objects.requireNonNullElse(limitValue, MAXIMUM)));
      int seen = 0;
      for(EchoDestinations.DocumentChoice choice : choices)
      {
         if((term.isEmpty() || choice.label().toLowerCase(Locale.ROOT).contains(term) || String.valueOf(choice.id()).contains(term))
            && seen++ >= skip && result.size() < limit)
         {
            result.add(new QPossibleValue<>(choice.id(), choice.label()));
         }
      }
      return result;
   }



   /***************************************************************************
    ** The selected size is a bounded identifier, never an expression.
    ***************************************************************************/
   static String media(Map<String, Serializable> otherValues) throws QException
   {
      Serializable value = otherValues == null ? null : otherValues.get(MEDIA_FIELD);
      if(value == null || String.valueOf(value).isBlank())
      {
         return null;
      }
      String text = String.valueOf(value);
      if(text.length() > 200)
      {
         throw new QBadRequestException("Choose a valid paper or label size first.");
      }
      return text;
   }
}
