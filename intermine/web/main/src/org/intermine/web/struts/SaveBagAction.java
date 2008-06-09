package org.intermine.web.struts;

/*
 * Copyright (C) 2002-2008 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.Date;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.intermine.ObjectStoreWriterInterMineImpl;
import org.intermine.web.logic.Constants;
import org.intermine.web.logic.bag.InterMineBag;
import org.intermine.web.logic.profile.Profile;
import org.intermine.web.logic.profile.ProfileManager;
import org.intermine.web.logic.results.PagedTable;
import org.intermine.web.logic.session.SessionMethods;

/**
 * Saves selected items in a new bag or combines with existing bag.
 *
 * @author Andrew Varley
 * @author Thomas Riley
 * @author Kim Rutherford
 */
public class SaveBagAction extends InterMineAction
{
    protected static final Logger LOG = Logger.getLogger(SaveBagAction.class);

    /**
     * @param mapping The ActionMapping used to select this instance
     * @param form The optional ActionForm bean for this request (if any)
     * @param request The HTTP request we are processing
     * @param response The HTTP response we are creating
     * @return an ActionForward object defining where control goes next
     * @exception Exception if the application business logic throws
     *  an exception
     */
    @Override
    public ActionForward execute(ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response)
        throws Exception {
        return saveBag(mapping, form, request, response);
    }

    /**
     * The batch size to use when we need to iterate through the whole result set.
     */
    public static final int BIG_BATCH_SIZE = 10000;

    /**
     * Save the selected objects to a bag on the session
     * @param mapping The ActionMapping used to select this instance
     * @param form The optional ActionForm bean for this request (if any)
     * @param request The HTTP request we are processing
     * @param response The HTTP response we are creating
     * @return an ActionForward object defining where control goes next
     */
    public ActionForward saveBag(ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 @SuppressWarnings("unused") HttpServletResponse response) {
        HttpSession session = request.getSession();
        Profile profile = (Profile) session.getAttribute(Constants.PROFILE);
        ServletContext servletContext = session.getServletContext();
        ObjectStore os = (ObjectStore) servletContext.getAttribute(Constants.OBJECTSTORE);
        PagedTable pt = SessionMethods.getResultsTable(session, request.getParameter("table"));
        SaveBagForm sbf = (SaveBagForm) form;
        ObjectStoreWriter uosw = ((ProfileManager) servletContext.getAttribute(Constants
                    .PROFILE_MANAGER)).getUserProfileObjectStore();

        String bagName = null;
        String operation = "";

        if (request.getParameter("saveNewBag") != null
                        || (sbf.getOperationButton() != null
                        && sbf.getOperationButton().equals("saveNewBag"))) {
            bagName = sbf.getNewBagName();
            operation = "saveNewBag";
        } else {
            bagName = sbf.getExistingBagName();
            operation = "addToBag";
        }

        if (bagName == null) {
            return null;
        }

        if (pt.isEmptySelection()) {
            ActionMessage actionMessage = new ActionMessage("errors.bag.empty");
            recordError(actionMessage, request);
            return mapping.findForward("results");
        }

        InterMineBag bag = profile.getSavedBags().get(bagName);

        if ((bag != null) && (!bag.getType().equals(pt.getSelectedClass()))) {
            ActionMessage actionMessage = new ActionMessage("bag.moreThanOneType");
            recordError(actionMessage, request);
            return mapping.findForward("results");
        }

//        WebTable allRows = pt.getAllRows();
//        boolean seenAllRows = false;
//        if (allRows.size() < 10 && !allRows.isSizeEstimate()) {
//            // hack to avoid problems with results from quick search - ignore the column type
//            // if all rows are selected
//            seenAllRows = true;
//        }

        // First pass through, just to check types are compatible.
//        for (int i = 0; i < sbf.getSelectedObjects().length; i++) {
//            String selectedObjectString = sbf.getSelectedObjects()[i];
//            if (!selectedObjectString.matches(".*,.*,.*") && seenAllRows) {
//                // ignore the column type as we're going to iterate over all rows
//                continue;
//            }
//            int indexOfFirstComma = selectedObjectString.indexOf(",");
//            String columnIndexString = selectedObjectString.substring(0, indexOfFirstComma);
//            int columnIndex = Integer.parseInt(columnIndexString);
//            Path columnPath = allRows.getColumns().get(columnIndex).getPath();
//            String columnType = null;
//            String cls = columnPath.getStartClassDescriptor().getUnqualifiedName();
//            if (cls.equals("Synonym")) {
//                int indexOfUnderscore = selectedObjectString.indexOf("_");
//                columnType = selectedObjectString.substring(++indexOfUnderscore);
//            } else {
//                columnType = columnPath.getLastClassDescriptor().getName();
//            }
//            objectTypes.add(TypeUtil.unqualifiedName(columnType));
//        }
//

        ObjectStoreWriter osw = null;
        try {
            if (bag == null) {
                bag = new InterMineBag(bagName, pt.getSelectedClass(), null, new Date(), os,
                        profile.getUserId(), uosw);
                profile.saveBag(bagName, bag);
            }

            osw = new ObjectStoreWriterInterMineImpl(os);

            pt.addSelectedToBag(osw, bag.getOsb());

            recordMessage(new ActionMessage("bag.saved", bagName), request);
            SessionMethods.invalidateBagTable(session, bagName);
        } catch (ObjectStoreException e) {
            LOG.error(e);
            ActionMessage actionMessage =
                new ActionMessage("An error occured while save the bag");
            recordError(actionMessage, request);
            return mapping.findForward("results");
        } finally {
            try {
                if (osw != null) {
                    osw.close();
                }
            } catch (ObjectStoreException e) {
                // empty
            }
        }
        if (operation.equals("saveNewBag")) {
            return new ForwardParameters(mapping.findForward("bag")).addParameter("bagName",
                bag.getName()).forward();
        } else {
            return mapping.findForward("results");
        }
    }
}
