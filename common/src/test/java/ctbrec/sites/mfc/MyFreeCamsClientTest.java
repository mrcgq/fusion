package ctbrec.sites.mfc;


import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import ctbrec.ReflectionUtil;
import ctbrec.StringUtil;

public class MyFreeCamsClientTest {


    @Test
    public void testMessageParsing() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        MyFreeCamsClient client = MyFreeCamsClient.getInstance();
        StringBuilder input = new StringBuilder("00001933 0 439895060 1 0 00001933 0 439895060 1 0 0000208 0 439895060 0 112 00038...");
        Message msg = ReflectionUtil.call(client, "parseMessage", input);
        assertEquals(33, msg.getType());
        assertEquals(0, msg.getSender());
        assertEquals(439895060, msg.getReceiver());
        assertEquals(1, msg.getArg1());
        assertEquals(0, msg.getArg2());
        assertTrue(StringUtil.isBlank(msg.getMessage()));

        input = new StringBuilder("00207820 439461784 439895060 12 507930 %7B%22lv%22%3A4%2C%22nm%22%3A%22Nivea%22%2C%22pid%22%3A1%2C%22sid%22%3A439461784%2C%22uid%22%3A507930%2C%22vs%22%3A12%2C%22u%22%3A%7B%22age%22%3A33%2C%22avatar%22%3A1%2C%22blurb%22%3A%22I%20love%20when%20my%20nose%20touch%20your%20belly%20when%20I%20do%20you%20a%20blowjob!%20When%20I%20look%20into%20your%20horny%20eyes%20when%22%2C%22camserv%22%3A1367%2C%22chat_color%22%3A%22FF0000%22%2C%22chat_font%22%3A0%2C%22chat_opt%22%3A1%2C%22ethnic%22%3A%22Caucasian%22%2C%22photos%22%3A74%2C%22profile%22%3A1%2C%22status%22%3A%22%22%7D%2C%22m%22%3A%7B%22camscore%22%3A7505.700%2C%22continent%22%3A%22EU%22%2C%22flags%22%3A605224%2C%22hidecs%22%3Atrue%2C%22kbit%22%3A0%2C%22lastnews%22%3A0%2C%22mg%22%3A0%2C%22missmfc%22%3A2%2C%22new_model%22%3A0%2C%22rank%22%3A0%2C%22rc%22%3A20%2C%22sfw%22%3A0%2C%22topic%22%3A%22hi%253A)%255Bnone%255D-Topless%252C500-snap4life%252C50-spanks%252C180-flash%252C700-10%2520mins%2520of%2520Nora%2520fun%252C666-shot%252C27%252C270%2520%253C3%22%7D%2C%22x%22%3A%7B%22fcext%22%3A%7B%22sm%22%3A%22%22%2C%22sfw%22%3A0%7D%2C%22share%22%3A%7B%22follows%22%3A11%2C%22albums%22%3A0%2C%22clubs%22%3A0%2C%22tm_album%22%3A0%2C%22collections%22%3A0%2C%22stores%22%3A0%2C%22goals%22%3A0%2C%22polls%22%3A0%2C%22things%22%3A0%2C%22recent_album_tm%22%3A0%2C%22recent_club_tm%22%3A0%2C%22recent_collection_tm%22%3A0%2C%22recent_goal_tm%22%3A0%2C%22recent_item_tm%22%3A0%2C%22recent_poll_tm%22%3A0%2C%22recent_story_tm%22%3A0%2C%22recent_album_thumb%22%3A%22%22%2C%22recent_club_thumb%22%3A%22%22%2C%22recent_collection_thumb%22%3A%22%22%2C%22recent_goal_thumb%22%3A%22%22%2C%22recent_item_thumb%22%3A%22%22%2C%22recent_poll_thumb%22%3A%22%22%2C%22recent_story_thumb%22%3A%22%22%2C%22recent_album_title%22%3A%22%22%2C%22recent_club_title%22%3A%22%22%2C%22recent_collection_title%22%3A%22%22%2C%22recent_goal_title%22%3A%22%22%2C%22recent_item_title%22%3A%22%22%2C%22recent_poll_title%22%3A%22%22%2C%22recent_story_title%22%3A%22%22%2C%22recent_album_slug%22%3A%22%22%2C%22recent_collection_slug%22%3A%22%22%2C%22tipmenus%22%3A0%7D%7D%7D");
        msg = ReflectionUtil.call(client, "parseMessage", input);
        assertEquals(20, msg.getType());
        assertEquals(439461784, msg.getSender());
        assertEquals(439895060, msg.getReceiver());
        assertEquals(12, msg.getArg1());
        assertEquals(507930, msg.getArg2());
        assertEquals(JSONObject.class, new JSONObject(msg.getMessage()).getClass()); // make sure we have a parsable JSON message
    }
}
