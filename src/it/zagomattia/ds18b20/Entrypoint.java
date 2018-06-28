/*
 * Project: DB18B20 OneWire sensor API Wrapper
 * Copyright (c) 2018 Mattia Zago
 *
 * @author Mattia Zago - dev@zagomattia.it
 */

package it.zagomattia.ds18b20;

import com.dalsemi.onewire.OneWireAccessProvider;
import com.dalsemi.onewire.OneWireException;
import com.dalsemi.onewire.adapter.DSPortAdapter;
import com.dalsemi.onewire.container.OneWireContainer;
import com.dalsemi.onewire.container.TemperatureContainer;

import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Main application Entry Point.
 */
public class Entrypoint {
    
    /**
     * CSV Format or the output file.
     */
    private static final CSVFormat csvFormat = CSVFormat.EXCEL.withHeader("uid", "timestamp", "temperature")
            .withFirstRecordAsHeader();
    
    public static void main(String[] args) {
        parseCLI(args);
    }
    
    /**
     * Parse the Command Line arguments and starts the dump.
     *
     * @param args
     */
    private static void parseCLI(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(getCLIOptions(), args);
            
            if (cmd.hasOption("h")) {
                printHelp();
                System.exit(0);
            }
            
            if (!cmd.hasOption("d")) {
                throw new MissingArgumentException("Missing argument for data folder. Exiting.");
            }
            
            if (!cmd.hasOption("p")) {
                throw new MissingArgumentException("Missing sensor COM port. Exiting.");
            }
            
            Path dataPath = Paths.get(cmd.getOptionValue("d"));
            
            if (dataPath.toFile().exists()) {
                if (dataPath.toFile().isFile()) {
                    throw new IllegalArgumentException("Data path is a file and not a folder.");
                }
            }
            else if (dataPath.toFile().mkdirs()) {
                System.out.println("Successfully created data folder.");
            }
            else {
                System.err.println("Cannot create data folder. Exiting.");
            }
            
            Integer waitingTime = cmd.hasOption("w") ? Integer.valueOf(cmd.getOptionValue("w")) : 0;
            
            String adapterName = cmd.hasOption("a") ? cmd.getOptionValue("a") : "{DS9097E}";
            String adapterPort = cmd.getOptionValue("p");
    
            startAdaptersListener(adapterName, adapterPort,dataPath, waitingTime);
            
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Gets the options for the command line interface.
     *
     * @return Options for Apache CLI.
     */
    private static Options getCLIOptions() {
        Option help = Option.builder("h").required(false).longOpt("help").argName("help").hasArg(false)
                .desc("Print this help page and exit.").build();
        
        Option dataFolder = Option.builder("d").required(false).longOpt("data").argName("Data Path").hasArg(true)
                .desc("Path for the data folder.").build();
        
        Option wait = Option.builder("w").required(false).longOpt("wait-time").argName("Waiting time").hasArg(true)
                .desc("The system will wait this amount of time at each cycle. Default is zero.").build();
        
        Option adapterName = Option.builder("a").required(false).longOpt("adapter-name").argName("Adapter Name")
                .hasArg(true)
                .desc("The adapter name. (Format: {XXXX}). Tshe default value is '{DS9097E}' that indicates the "
                        + "DS18B20 sensor.").build();
        
        Option adapterCOMPort = Option.builder("p").required(false).longOpt("adapter-port").argName("Adapter Port")
                .hasArg(true).desc("The adapter COM Port (Format: COMX).").build();
        
        return new Options().addOption(dataFolder).addOption(wait).addOption(adapterName).addOption(adapterCOMPort)
                .addOption(help);
    }
    
    /**
     * Print the help page.
     */
    private static void printHelp() {
        new HelpFormatter().printHelp("java -jar DS18B20.jar", getCLIOptions(), true);
    }
    
    /**
     * Starts the listener for the provided adapter.
     *
     * @throws OneWireException
     */
    private static void startAdaptersListener(String adapterName, String portName, Path dataFolder, Integer waitingTime)
            throws OneWireException {
        
        DSPortAdapter portAdapter = OneWireAccessProvider.getAdapter(adapterName, portName);
        
        if (!portAdapter.adapterDetected()) {
            System.err.println("Adapter not connected.");
            System.exit(-1);
        }
        portAdapter.targetAllFamilies();
        
        // Block access to other snippet
        portAdapter.beginExclusive(true);
        
        portAdapter.reset();
        
        // Check not only the "alarm" sensors
        portAdapter.setSearchAllDevices();
        
        while (true) {
            while (portAdapter.findNextDevice()) {
                analyseOne(portAdapter.getDeviceContainer(), dataFolder);
            }
            try {
                Thread.sleep(waitingTime);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Dump the value of an adapter.
     *
     * @param deviceContainer Adapter object container.
     * @param dataFolder      Folder to store the data.
     */
    private static void analyseOne(OneWireContainer deviceContainer, Path dataFolder) {
        TemperatureContainer temperatureContainer;
        if (deviceContainer instanceof TemperatureContainer) {
            temperatureContainer = (TemperatureContainer) deviceContainer;
        }
        else {
            return;
        }
        
        try (FileWriter fileWriter = new FileWriter(
                dataFolder.toString() + deviceContainer.getAddressAsString() + ".csv", true)) {
            CSVPrinter printer = new CSVPrinter(fileWriter, csvFormat);
            
            Object[] out = new Object[3];
            out[0] = deviceContainer.getAddressAsString();
            
            temperatureContainer.doTemperatureConvert(temperatureContainer.readDevice());
            
            out[1] = LocalDateTime.now().toString();
            out[2] = temperatureContainer.getTemperature(temperatureContainer.readDevice());
            
            printer.printRecord(out);
            printer.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
