package ctbrec.recorder.postprocessing;

import ctbrec.Config;
import ctbrec.Recording;
import ctbrec.notes.ModelNotesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractPlaceholderAwarePostProcessorTest extends AbstractPpTest {

    Recording rec;
    Config config;
    Move placeHolderAwarePp;
    ModelNotesService modelNotesService;

    @Override
    @BeforeEach
    public void setup() throws IOException {
        super.setup();
        rec = new Recording();
        rec.setModel(mockModel());
        rec.setAbsoluteFile(original);
        rec.setPostProcessedFile(postProcessed);
        rec.setStartDate(now);
        rec.setSingleFile(true);
        config = mockConfig();
        placeHolderAwarePp = new Move();
        modelNotesService = mock(ModelNotesService.class);

        when(modelNotesService.loadModelNotes(any())).thenReturn(Optional.of("tag,_foo,_bar"));
    }

    @Test
    void testModelNameReplacement() {
        String input = "asdf_${modelName}_asdf";
        assertEquals("asdf_Mockita_Boobilicious_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
        input = "asdf_${modelDisplayName}_asdf";
        assertEquals("asdf_Mockita Boobilicious_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
        input = "asdf_$sanitize(${modelName})_asdf";
        assertEquals("asdf_Mockita_Boobilicious_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
    }

    @Test
    void testSiteNameReplacement() {
        String input = "asdf_${siteName}_asdf";
        assertEquals("asdf_Stripchat_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
        input = "asdf_$sanitize(${siteName})_asdf";
        assertEquals("asdf_Stripchat_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
    }

    @Test
    void testUtcTimeReplacement() {
        // without user defined pattern
        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withLocale(Locale.US)
                .withZone(ZoneOffset.UTC)
                .format(rec.getStartDate());
        String input = "asdf_$format(${utcDateTime})_asdf";
        assertEquals("asdf_" + date + "_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));

        // with user defined pattern
        date = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withLocale(Locale.US)
                .withZone(ZoneOffset.UTC)
                .format(rec.getStartDate());
        input = "asdf_$format(${utcDateTime},yyyyMMdd-HHmmss)_asdf";
        assertEquals("asdf_" + date + "_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));

        // multiple occurences with user defined patterns
        date = DateTimeFormatter.ofPattern("yyyy-MM-dd/yyyy")
                .withLocale(Locale.US)
                .withZone(ZoneOffset.UTC)
                .format(rec.getStartDate());
        input = "asdf_$format(${utcDateTime},yyyy)-$format(${utcDateTime},MM)-$format(${utcDateTime},dd)/$format(${utcDateTime},yyyy)_asdf";
        assertEquals("asdf_" + date + "_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
    }

    @Test
    void testLocalTimeReplacement() {
        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withLocale(Locale.US)
                .withZone(ZoneId.systemDefault())
                .format(rec.getStartDate());
        String input = "asdf_$format(${localDateTime})_asdf";
        assertEquals("asdf_" + date + "_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));

        date = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withLocale(Locale.US)
                .withZone(ZoneId.systemDefault())
                .format(rec.getStartDate());
        input = "asdf_$format(${localDateTime},yyyyMMdd-HHmmss)_asdf";
        assertEquals("asdf_" + date + "_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
    }

    @Test
    void testEpochReplacement() {
        long epoch = now.toEpochMilli() / 1000;
        String input = "asdf_${epochSecond}_asdf";
        assertEquals("asdf_" + epoch + "_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
    }

    @Test
    void testFileSuffixReplacement() {
        String input = "asdf_${fileSuffix}_asdf";
        assertEquals("asdf_ts_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
    }

    @Test
    void testRecordingsDirReplacement() {
        String input = "asdf_${recordingsDir}_asdf";
        assertEquals("asdf_" + recDir.toString() + "_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
    }

    @Test
    void testAbsolutePathReplacement() {
        String input = "asdf_${absolutePath}_asdf";
        assertEquals("asdf_" + postProcessed.getAbsolutePath() + "_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
    }

    @Test
    void testAbsoluteParentPathReplacement() {
        String input = "asdf_${absoluteParentPath}_asdf";
        assertEquals("asdf_" + postProcessed.getParentFile().toString() + "_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
    }

    @Test
    void testModelNotesReplacement() {
        String input = "asdf_${modelNotes}_asdf";
        assertEquals("asdf_tag,_foo,_bar_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config, modelNotesService)));
    }

    @Test
    void testPlaceholderDefaultValues() {
        String input = "asdf_$orElse(${modelGroupName},$orElse($sanitize(${modelName}),anonymous))_asdf";
        PostProcessingContext ctx = createPostProcessingContext(rec, null, config);
        ctx.getRecording().getModel().setName(null);
        assertEquals("asdf_anonymous_asdf", placeHolderAwarePp.fillInPlaceHolders(input, ctx));

        input = "asdf_$orElse(${modelGroupName},$format(${utcDateTime},yyyy))_asdf";
        int year = LocalDate.now().getYear();
        assertEquals("asdf_" + year + "_asdf", placeHolderAwarePp.fillInPlaceHolders(input, ctx));

        ctx.getRecording().setStartDate(null);
        input = "asdf_$orElse(${modelGroupName},$orElse($format(${utcDateTime},yyyy),anonymous))_asdf";
        assertEquals("asdf_anonymous_asdf", placeHolderAwarePp.fillInPlaceHolders(input, ctx));

        input = "asdf_$orElse(${modelGroupName},$orElse($format(${utcDateTime}),anonymous))_asdf";
        assertEquals("asdf_anonymous_asdf", placeHolderAwarePp.fillInPlaceHolders(input, ctx));
    }

    @Test
    void testMissingValueForPlaceholder() {
        String input = "asdf_${modelNotes}_asdf";
        assertEquals("asdf_${modelNotes}_asdf", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
    }

    @Test
    void testFunctionCalls() {
        String input = "$upper(${modelName})";
        assertEquals("MOCKITA_BOOBILICIOUS", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));

        input = "$upper($orElse(${doesNotExist},mockita boobilicious))";
        assertEquals("MOCKITA BOOBILICIOUS", placeHolderAwarePp.fillInPlaceHolders(input, createPostProcessingContext(rec, null, config)));
    }
}
