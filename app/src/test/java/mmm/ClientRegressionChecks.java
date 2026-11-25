package mmm;

/** The same regression checks run from Maven and standalone client builds. */
public final class ClientRegressionChecks {
  public static void main(String[] args) throws Exception {
    ModelRepositoryTest.main(args);
    FeatureTest.main(args);
    SoundShapeTest.main(args);
    EnsembleDataTest.main(args);
    ExpressionTest.main(args);
    WireJsonTest.main(args);
    PianoTest.main(args);
    PitchRangeTest.main(args);
    InstrumentSoundTest.main(args);
    StringInstrumentTest.main(args);
    EmotionCatalogTest.main(args);
  }
}
