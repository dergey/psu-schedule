package com.sergey.zhuravlev.pgu.schedule.parser;

import android.util.Log;

import com.sergey.zhuravlev.pgu.schedule.exception.ParseScheduleException;
import com.sergey.zhuravlev.pgu.schedule.exception.WeekPeriodException;
import com.sergey.zhuravlev.pgu.schedule.model.Classwork;
import com.sergey.zhuravlev.pgu.schedule.model.ClassworkTime;
import com.sergey.zhuravlev.pgu.schedule.model.DayOfWeek;
import com.sergey.zhuravlev.pgu.schedule.model.Group;
import com.sergey.zhuravlev.pgu.schedule.model.Schedule;
import com.sergey.zhuravlev.pgu.schedule.model.WeekColor;
import com.sergey.zhuravlev.pgu.schedule.model.WeekPeriod;
import com.sergey.zhuravlev.pgu.schedule.utils.Utils;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScheduleParser {

    private static final Pattern classwork = Pattern.compile("(?:([0-9]+-[0-9]+н\\.?)\\s+)?(.*?)((?:[А-Я][а-я]+\\s[А-Я]\\.[А-Я]\\.,?\\s?)+)\\s?(?:ауд\\.?([0-9]{1,3}н?))?");

    private static final String russianAlphabet = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    private static final String numbers = "1234567890";

    public static Schedule parse(XWPFDocument document) throws ParseScheduleException {
        List<Classwork> rawSchedule = null;
        for (XWPFTable table : document.getTables()) {
            try {
                rawSchedule = parseTable(table);
            } catch (ParseScheduleException e) {
                continue;
            }
            if (rawSchedule.size() > 0)
                break;
        }
        if (rawSchedule == null)
            throw new ParseScheduleException("Can't found target schedule in doc");
        return new Schedule(rawSchedule);
    }

    private static List<Classwork> parseTable(XWPFTable rawTable) throws ParseScheduleException {
        List<Classwork> rawSchedule = new ArrayList<>();
        DayOfWeek dayOfWeek = null;
        List<Group> groups = new ArrayList<>();

        for (XWPFTableRow xwpfTableRow : rawTable.getRows()) {
            ClassworkTime classworkTime = null;
            int column = 0;
            for (XWPFTableCell xwpfTableCell : xwpfTableRow.getTableCells()) {
                Log.d("Parser", "CELL " + xwpfTableCell.getText());
                if (isGroup(xwpfTableCell.getText())) {
                    groups.add(getGroup(xwpfTableCell.getText()));
                    Log.d("Parser", "\tGROUP " + groups.size());
                } else if (isDayOfWeek(xwpfTableCell.getText())) {
                    dayOfWeek = getDayOfWeek(xwpfTableCell.getText());
                    Log.d("Parser", "\tDAY_OF_WEEK " + dayOfWeek);
                } else if (isClassworkPeriod(xwpfTableCell.getText())) {
                    classworkTime = getClassworkPeriod(xwpfTableCell.getText());
                    Log.d("Parser", "\tPERIOD " + classworkTime);
                } else if (dayOfWeek != null && classworkTime != null && haveText(xwpfTableCell.getText())) {
                    if (column >= groups.size()) column = groups.size() - 1;
                    rawSchedule.addAll(getClasswork(xwpfTableCell.getText(), dayOfWeek, classworkTime, getWeekColor(xwpfTableCell), groups.get(column)));
                    column++;
                }
            }
        }
        return rawSchedule;
    }

    private static WeekColor getWeekColor(XWPFTableCell xwpfTableCell) {
        Log.d("Parser.WeekColor", "color " + xwpfTableCell.getColor());
        return xwpfTableCell.getColor()!= null ? WeekColor.GREEN : WeekColor.WHILE;
    }

    private static boolean haveText(String text) {
        if (text.isEmpty()) return false;

        boolean haveText = false;
        for (char c : text.toCharArray()) {
            if (c != ' ')
                haveText = true;
        }
        return haveText;
    }

    public static Collection<Classwork> getClasswork(String text, DayOfWeek dayOfWeek, ClassworkTime classworkTime, WeekColor weekColor, Group group) {
        List<Classwork> classworks = new ArrayList<>();
        Matcher matcher = classwork.matcher(text);
        Log.d("Parser", "\tCLASSWORK " + text);
        if (matcher.find()) {
            classworks.add(new Classwork(getWeekPeriod(matcher), weekColor, dayOfWeek, classworkTime, group, getClassworkTitle(matcher), getTeacher(matcher), getAudience(matcher)));
            while (matcher.find()) {
                String audience = getAudience(matcher);
                classworks.add(new Classwork(getWeekPeriod(matcher), weekColor, dayOfWeek, classworkTime, group, getClassworkTitle(matcher), getTeacher(matcher), audience));
                if (audience != null && !audience.isEmpty() && classworks.size() > 0) {
                    for (int i = classworks.size() - 1; i >= 0; i--) {
                        if (classworks.get(i).getAudience() != null && classworks.get(i).getAudience().isEmpty()) break;
                        classworks.get(i).setAudience(audience);
                    }
                }
            }
        } else {
            classworks.add(new Classwork(null, weekColor, dayOfWeek, classworkTime, group, text, null, null));
        }
        return classworks;
    }

    private static boolean isDayOfWeek(String text) {
        try {
            getDayOfWeek(text);
        } catch (ParseScheduleException e) {
            return false;
        }
        return true;
    }

    private static DayOfWeek getDayOfWeek(String text) throws ParseScheduleException {
        switch (Utils.filterString(text, russianAlphabet)) {
            case "понедельник":
                return DayOfWeek.MONDAY;
            case "вторник":
                return DayOfWeek.TUESDAY;
            case "среда":
                return DayOfWeek.WEDNESDAY;
            case "четверг":
                return DayOfWeek.THURSDAY;
            case "пятница":
                return DayOfWeek.FRIDAY;
            case "суббота":
                return DayOfWeek.SATURDAY;
            default:
                throw new ParseScheduleException("DayOfWeek not parsable");
        }
    }

    private static boolean isClassworkPeriod(String text) {
        try {
            getClassworkPeriod(text);
        } catch (ParseScheduleException e) {
            return false;
        }
        return true;
    }

    private static ClassworkTime getClassworkPeriod(String text) throws ParseScheduleException {
        switch (Utils.filterString(text, numbers)) {
            case "8301000":
                return ClassworkTime.FIRST_CLASSWORK;
            case "10151145":
                return ClassworkTime.SECOND_CLASSWORK;
            case "12151345":
                return ClassworkTime.THIRD_CLASSWORK;
            case "14151545":
                return ClassworkTime.FOURTH_CLASSWORK;
            case "16001730":
                return ClassworkTime.FIFTH_CLASSWORK;
            case "17451915":
                return ClassworkTime.SIXTH_CLASSWORK;
            case "19252050":
                return ClassworkTime.SEVENTH_CLASSWORK;
            default:
                throw new ParseScheduleException("ClassworkTime not parsable");
        }

    }

    private static boolean isGroup(String text) {
        try {
            getGroup(text);
        } catch (ParseScheduleException e) {
            return false;
        }
        return true;
    }

    private static Group getGroup(String text) throws ParseScheduleException {
        int year;

        try {
            String yearString = Utils.filterString(text, numbers);
            year = Integer.valueOf(yearString);
        } catch (NumberFormatException error) {
            throw new ParseScheduleException("Group year not parsable");
        }

        String name = Utils.filterString(text, russianAlphabet);

        if (name.equals("арх")) {
            return new Group("архитектура", year);
        } else if (name.equals("гео")) {
            return new Group("геодезия", year);
        } else {
            throw new ParseScheduleException("Group not parsable");
        }

    }

    private static WeekPeriod getWeekPeriod(Matcher matcher) {
        try {
            Log.d("Parser.WeekPeriod", "string " + matcher.group(1));
            return WeekPeriod.of(matcher.group(1));
        } catch (WeekPeriodException e) {
            Log.w("Parser.WeekPeriod", e.getMessage());
            return null;
        }
    }

    private static String getClassworkTitle(Matcher matcher) {
        Log.d("Parser.ClassworkTitle", "string " + matcher.group(2));
        return matcher.group(2);
    }

    private static String getTeacher(Matcher matcher) {
        Log.d("Parser.Teacher", "string " + matcher.group(3));
        return matcher.group(3);
    }

    private static String getAudience(Matcher matcher) {
        Log.d("Parser.Audience", "string " + matcher.group(4));
        return matcher.group(4);
    }

}
