package org.carlspring.strongbox.controllers;

import org.carlspring.strongbox.configuration.Configuration;
import org.carlspring.strongbox.configuration.ConfigurationManager;
import org.carlspring.strongbox.controllers.support.ErrorXmlHandler;
import org.carlspring.strongbox.resource.ResourceCloser;
import org.carlspring.strongbox.xml.parsers.GenericParser;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;
import java.io.InputStream;
import java.io.OutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Provides common subroutines that will be useful for any backend controllers.
 *
 * @author Alex Oreshkevich
 */
public abstract class BaseController
{

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    protected ObjectMapper objectMapper;

    @Inject
    protected ConfigurationManager configurationManager;

    protected ResponseEntity toXmlError(String message,
                                        HttpStatus httpStatus)
            throws JAXBException
    {
        return ResponseEntity.status(httpStatus)
                             .body(new GenericParser<>(ErrorXmlHandler.class).serialize(new ErrorXmlHandler(message)));
    }

    protected ResponseEntity toXmlError(String message)
            throws JAXBException
    {
        return toXmlError(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    protected ResponseEntity toError(String message)
    {
        return toError(new RuntimeException(message));
    }

    protected ResponseEntity toError(Throwable cause)
    {
        logger.error(cause.getMessage(), cause);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(cause.getMessage());
    }

    protected Configuration getConfiguration()
    {
        return configurationManager.getConfiguration();
    }

    protected void copyToResponse(InputStream is,
                                  HttpServletResponse response)
            throws Exception
    {
        OutputStream os = response.getOutputStream();

        try
        {
            long totalBytes = 0L;

            int readLength;
            byte[] bytes = new byte[4096];
            while ((readLength = is.read(bytes, 0, bytes.length)) != -1)
            {
                // Write the artifact
                os.write(bytes, 0, readLength);
                os.flush();

                totalBytes += readLength;
            }

            response.setHeader("Content-Length", Long.toString(totalBytes));
            response.flushBuffer();
        }
        finally
        {
            ResourceCloser.close(is, logger);
            ResourceCloser.close(os, logger);
        }
    }

}
