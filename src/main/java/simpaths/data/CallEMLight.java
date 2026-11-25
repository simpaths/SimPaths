package simpaths.data;

import org.apache.commons.lang3.SystemUtils;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

public class CallEMLight {

    //-------------------------------------------------------------------------------------------------------------
    /**
     *	CALLING UKMOD LIGHT IN THIS SIMULATION RUN TO GIVE THE USER THE OPPORTUNITY TO CREATE UKMOD OUTPUT POPULATION from which to generate population of households and persons after tax and benefit treatment
     **/
    //-------------------------------------------------------------------------------------------------------------

    public static void run() {
        //UKMOD Light must be separately installed on the machine where the simulation is run, in the default location (Program Files (x86) on
        //Windows machines).  Note, UKMOD currently only works on Windows operating systems, not Linux or MAC OS.
        if(SystemUtils.IS_OS_WINDOWS) {
            try {
                String ukmodDirectoryString = new String("C:\\Program Files\\EM_LightUI\\EM_LightUI.exe");
                File tempDir = new File(ukmodDirectoryString);
                if(tempDir.exists()) {
                    ProcessBuilder ukmodProcess =
                            new ProcessBuilder(ukmodDirectoryString);
                    ukmodProcess.redirectErrorStream(true);
                    try {

                        Process p = ukmodProcess.start();
                        assert ukmodProcess.redirectInput() == ProcessBuilder.Redirect.PIPE;
                        assert p.getInputStream().read() == -1;

                        String title = "Specify UKMOD Light Output Path";
                        String text = "<html><h2 style=\"text-align: center; font-size:120%; padding: 10pt; padding-bottom: 0;\">"
                                + "When running UKMOD Light, please save the output data to the following location:<br><br>"
                                + Parameters.UKMOD_OUTPUT_DIRECTORY + "</h2></html>";
                        FormattedDialogBox.create(title, text, 800, 200, null, false, true, true);

                        //Make process wait until UKMOD has been terminated
                        p.waitFor();

                    } catch (IOException | InterruptedException e) {

                        e.printStackTrace();
                    }
                } else {

                    boolean pathcorrect = false;
                    while (!pathcorrect) {
                        String ukmodInstallPath = null;
                        JFrame frame = new JFrame("UKMOD Light not found in default location");
                        JOptionPane.showMessageDialog(frame, "UKMOD Light not found in the default location. Please select the EM_LightUI.exe file on the next screen.");
                        final JFileChooser fc = new JFileChooser();
                        fc.setCurrentDirectory(new File(System.getProperty("user.home")));
                        int returnValue = fc.showOpenDialog(fc);
                        if (returnValue == JFileChooser.APPROVE_OPTION) {
                            File fileEMUI = fc.getSelectedFile();
                            ukmodInstallPath = fileEMUI.getPath(); //Get path of the selected EM_UI file

                            ProcessBuilder ukmodProcess =
                                    new ProcessBuilder(ukmodInstallPath);
                            ukmodProcess.redirectErrorStream(true);
                            try {

                                Process p = ukmodProcess.start();
                                assert ukmodProcess.redirectInput() == ProcessBuilder.Redirect.PIPE;
                                assert p.getInputStream().read() == -1;
                                pathcorrect = true;
                                //Make process wait until UKMOD has been terminated

                                String title = "Specify UKMOD Light Output Path";
                                String text = "<html><h2 style=\"text-align: center; font-size:120%; padding: 10pt; padding-bottom: 0;\">"
                                        + "When running UKMOD Light, please save the output data to the following location:<br><br>"
                                        + Parameters.UKMOD_OUTPUT_DIRECTORY + "</h2></html>";
                                FormattedDialogBox.create(title, text, 800, 200, null, false, true, true);

                                p.waitFor();

                            } catch (IOException | InterruptedException e) {

                                //PB: If getting IOException, would like to provide an input window for where UKMOD is installed - doesn't have to be C drive.
                                JOptionPane.showMessageDialog(null,
                                        "Please check the path provided is correct",
                                        "Unable to execute UKMOD Light",
                                        JOptionPane.INFORMATION_MESSAGE);
                                pathcorrect = false;
                                e.printStackTrace();
                            }
                        }
                        else if (returnValue == JFileChooser.CANCEL_OPTION) {
                            JFrame frameCancel = new JFrame("Choice cancelled");
                            JOptionPane.showMessageDialog(frameCancel, "UKMOD Light not found in the default location and no location provided. Skipping UKMOD Light step.");
                            break;
                        }
                    }

                }
            } catch (Exception e) {

                e.printStackTrace();
            }
        }
        else {
            JOptionPane.showMessageDialog(null,
                    "<html><p align=center style=\"font-size:120%;\">UKMOD Light is currently only available on the Windows operating system.<br>"
                            + "Before running the simulation, please ensure that the required files<br>"
                            + "containing the output of UKMOD Light exist in the following directory:</p><br>"
                            + "<h1 align=center>" + Parameters.UKMOD_OUTPUT_DIRECTORY + "</h1></html>",
                    "Unable to execute UKMOD Light",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

}
