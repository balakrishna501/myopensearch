package com.gmestri.elasticapi.service;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;

@Service
public class DateRangeParser {

    private static final List<String> DATE_UNITS = Arrays.asList("day", "week", "month", "year");

    public boolean isDateRangeExpression(CoreMap sentence, int index, String currentValue) {
        String currentTokenLemma = sentence.get(CoreAnnotations.TokensAnnotation.class).get(index).lemma().toLowerCase();

        if (DATE_UNITS.contains(currentTokenLemma)) {
            if (currentValue.trim().matches("\\d+") || (currentTokenLemma.equals("months") || currentTokenLemma.equals("years") || currentTokenLemma.equals("weeks") || currentTokenLemma.equals("days")) && index > 0 && sentence.get(CoreAnnotations.TokensAnnotation.class).get(index - 1).lemma().toLowerCase().equals("last")) {
                return true;
            }
        } else if (currentTokenLemma.equals("yesterday") || currentTokenLemma.equals("today") || currentTokenLemma.equals("tomorrow")) {
            return true;
        } else if (index > 0 && (sentence.get(CoreAnnotations.TokensAnnotation.class).get(index - 1).lemma().toLowerCase().equals("last") || sentence.get(CoreAnnotations.TokensAnnotation.class).get(index - 1).lemma().toLowerCase().equals("previous")) && DATE_UNITS.contains(currentTokenLemma)) {
            return true;
        } else if (index > 1 && sentence.get(CoreAnnotations.TokensAnnotation.class).get(index - 1).lemma().toLowerCase().equals("ago") && currentValue.trim().matches("\\d+") && DATE_UNITS.contains(currentTokenLemma)) {
            return true;
        } else if (index > 0 && sentence.get(CoreAnnotations.TokensAnnotation.class).get(index - 1).lemma().toLowerCase().equals("this") && DATE_UNITS.contains(currentTokenLemma)){
            return true;
        }
        return false;
    }

    public DateRange parseDateRange(CoreMap sentence, int index, String currentValue) {
        String currentTokenLemma = sentence.get(CoreAnnotations.TokensAnnotation.class).get(index).lemma().toLowerCase();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate;
        int endIndex = index + 1;

        if (currentTokenLemma.equals("yesterday")) {
            startDate = endDate.minusDays(1);
        } else if (currentTokenLemma.equals("tomorrow")) {
            startDate = endDate.plusDays(1);
        } else if (currentTokenLemma.equals("today")) {
            //do nothing, already set.
        } else if (index > 0 && sentence.get(CoreAnnotations.TokensAnnotation.class).get(index - 1).lemma().toLowerCase().equals("last")) {
            startDate = calculateRelativeDate(endDate, currentTokenLemma, -1);
            endIndex = index + 1;
        } else if (index > 0 && sentence.get(CoreAnnotations.TokensAnnotation.class).get(index - 1).lemma().toLowerCase().equals("previous")) {
            startDate = calculateRelativeDate(endDate, currentTokenLemma, -1);
            endIndex = index + 1;
        } else if (index > 1 && sentence.get(CoreAnnotations.TokensAnnotation.class).get(index - 1).lemma().toLowerCase().equals("ago") && currentValue.trim().matches("\\d+")) {
            int amount = Integer.parseInt(currentValue.trim());
            startDate = calculateRelativeDate(endDate, currentTokenLemma, -amount);
            endIndex = index + 1;
        } else if (index > 0 && sentence.get(CoreAnnotations.TokensAnnotation.class).get(index - 1).lemma().toLowerCase().equals("this")){
            startDate = calculateThis(endDate, currentTokenLemma);
            endIndex = index + 1;
        } else {
            return calculateNumber(sentence, index, currentValue);
        }

        return new DateRange(startDate, endDate, endIndex);
    }

    private LocalDate calculateRelativeDate(LocalDate endDate, String unit, int amount) {
        switch (unit) {
            case "day":
                return endDate.plusDays(amount);
            case "week":
                return endDate.plusWeeks(amount);
            case "month":
                return endDate.plusMonths(amount);
            case "year":
                return endDate.plusYears(amount);
            default:
                return endDate;
        }
    }

    private LocalDate calculateThis(LocalDate endDate, String unit){
        switch(unit){
            case "week":
                return endDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case "month":
                return endDate.withDayOfMonth(1);
            case "year":
                return endDate.withDayOfYear(1);
            default:
                return endDate;
        }
    }
    private DateRange calculateNumber(CoreMap sentence, int index, String currentValue){
        String currentTokenLemma = sentence.get(CoreAnnotations.TokensAnnotation.class).get(index).lemma().toLowerCase();
        int amount = 0;
        if (currentValue.trim().matches("\\d+")) {
            amount = Integer.parseInt(currentValue.trim());
        } else if ((currentTokenLemma.equals("months") || currentTokenLemma.equals("years") || currentTokenLemma.equals("weeks") || currentTokenLemma.equals("days")) && index > 0 && sentence.get(CoreAnnotations.TokensAnnotation.class).get(index - 1).lemma().toLowerCase().equals("last")) {
            amount = 3; // Default to 3 for "last"
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = calculateRelativeDate(endDate, currentTokenLemma, -amount);
        int endIndex = index;
        if (currentValue.trim().matches("\\d+")) {
            endIndex = index + 1;
        }
        return new DateRange(startDate, endDate, endIndex);
    }

    public static class DateRange {
        private LocalDate startDate;
        private LocalDate endDate;
        private int endIndex;

        public DateRange(LocalDate startDate, LocalDate endDate, int endIndex) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.endIndex = endIndex;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public int getEndIndex() {
            return endIndex;
        }
    }
}
