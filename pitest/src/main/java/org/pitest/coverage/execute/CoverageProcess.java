package org.pitest.coverage.execute;

import java.io.IOException;
import java.util.List;

import org.pitest.extension.TestUnit;
import org.pitest.functional.SideEffect1;
import org.pitest.mutationtest.CoverageReceiverThread;
import org.pitest.util.WrappingProcess;

public class CoverageProcess extends WrappingProcess {

  private final CoverageReceiverThread crt;

  public CoverageProcess(final Args processArgs,
      final SlaveArguments arguments, final int port, final List<TestUnit> tus,
      final SideEffect1<CoverageResult> handler) throws IOException {
    super(processArgs, arguments, CoverageSlave.class);

    this.crt = new CoverageReceiverThread(port, tus, handler);
    this.crt.start();
  }

  @Override
  public int waitToDie() throws InterruptedException {
    final int exitCode = super.waitToDie();
    this.crt.waitToFinish();
    return exitCode;
  }

}