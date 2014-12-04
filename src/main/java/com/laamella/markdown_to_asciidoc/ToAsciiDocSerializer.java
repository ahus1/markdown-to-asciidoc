package com.laamella.markdown_to_asciidoc;

import org.parboiled.common.StringUtils;
import org.pegdown.LinkRenderer;
import org.pegdown.Printer;
import org.pegdown.ast.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.parboiled.common.Preconditions.checkArgNotNull;

public class ToAsciiDocSerializer implements Visitor {
    protected Printer printer = new Printer();
    protected final Map<String, ReferenceNode> references = new HashMap<String, ReferenceNode>();
    protected final Map<String, String> abbreviations = new HashMap<String, String>();
    protected final LinkRenderer linkRenderer = new LinkRenderer();

    protected TableNode currentTableNode;
    protected int currentTableColumn;
    protected boolean inTableHeader;
    protected boolean inOrderedList;


    public String toAsciiDoc(RootNode astRoot) {
        checkArgNotNull(astRoot, "astRoot");
        astRoot.accept(this);
        return printer.getString();
    }

    public void visit(RootNode node) {
        for (ReferenceNode refNode : node.getReferences()) {
            visitChildren(refNode);
            references.put(normalize(printer.getString()), refNode);
            printer.clear();
        }
        for (AbbreviationNode abbrNode : node.getAbbreviations()) {
            visitChildren(abbrNode);
            String abbr = printer.getString();
            printer.clear();
            abbrNode.getExpansion().accept(this);
            String expansion = printer.getString();
            abbreviations.put(abbr, expansion);
            printer.clear();
        }
        visitChildren(node);
    }

    public void visit(AbbreviationNode node) {
    }

    public void visit(AutoLinkNode node) {
        printLink(linkRenderer.render(node));
    }

    public void visit(BlockQuoteNode node) {
        printer.println();
        repeat('=', 4);
        visitChildren(node);
        printer.println();
        repeat('=', 4);
        printer.println();
    }

    public void visit(BulletListNode node) {
        printer.println();
        visitChildren(node);
    }

    public void visit(CodeNode node) {
        printer.print('`');
        printer.printEncoded(node.getText());
        printer.print('`');
    }

    public void visit(DefinitionListNode node) {
        printIndentedTag(node, "dl");
    }

    public void visit(DefinitionNode node) {
        printTag(node, "dd");
    }

    public void visit(DefinitionTermNode node) {
        printTag(node, "dt");
    }

    public void visit(ExpImageNode node) {
        String text = printChildrenToString(node);
        printImageTag(linkRenderer.render(node, text));
    }

    public void visit(ExpLinkNode node) {
        String text = printChildrenToString(node);
        printLink(linkRenderer.render(node, text));
    }

    public void visit(HeaderNode node) {
        printer.println().println();
        repeat('=', node.getLevel());
        printer.print(' ');
        visitChildren(node);
    }

    private void repeat(char c, int times) {
        for (int i = 0; i < times; i++) {
            printer.print(c);
        }
    }

    public void visit(HtmlBlockNode node) {
        String text = node.getText();
        if (text.length() > 0) printer.println();
        printer.print(text);
    }

    public void visit(InlineHtmlNode node) {
        printer.print(node.getText());
    }

    public void visit(ListItemNode node) {
        printer.println();

        if(inOrderedList) {
            printer.print("1. ");
        } else {
            printer.print("* ");
        }
        visitChildren(node);
    }

    public void visit(MailLinkNode node) {
        printLink(linkRenderer.render(node));
    }

    public void visit(OrderedListNode node) {
        inOrderedList = true;

        printer.println();
        visitChildren(node);

        inOrderedList = false;
    }

    public void visit(ParaNode node) {
        printer.println().println();
        visitChildren(node);
    }

    public void visit(QuotedNode node) {
        switch (node.getType()) {
            case DoubleAngle:
                printer.print("«");
                visitChildren(node);
                printer.print("»");
                break;
            case Double:
                printer.print("“");
                visitChildren(node);
                printer.print("”");
                break;
            case Single:
                printer.print("‘");
                visitChildren(node);
                printer.print("’");
                break;
        }
    }

    public void visit(ReferenceNode node) {
        // reference nodes are not printed
    }

    public void visit(RefImageNode node) {
        String text = printChildrenToString(node);
        String key = node.referenceKey != null ? printChildrenToString(node.referenceKey) : text;
        ReferenceNode refNode = references.get(normalize(key));
        if (refNode == null) { // "fake" reference image link
            printer.print("![").print(text).print(']');
            if (node.separatorSpace != null) {
                printer.print(node.separatorSpace).print('[');
                if (node.referenceKey != null) printer.print(key);
                printer.print(']');
            }
        } else printImageTag(linkRenderer.render(node, refNode.getUrl(), refNode.getTitle(), text));
    }

    public void visit(RefLinkNode node) {
        String text = printChildrenToString(node);
        String key = node.referenceKey != null ? printChildrenToString(node.referenceKey) : text;
        ReferenceNode refNode = references.get(normalize(key));
        if (refNode == null) { // "fake" reference link
            printer.print('[').print(text).print(']');
            if (node.separatorSpace != null) {
                printer.print(node.separatorSpace).print('[');
                if (node.referenceKey != null) printer.print(key);
                printer.print(']');
            }
        } else printLink(linkRenderer.render(node, refNode.getUrl(), refNode.getTitle(), text));
    }

    public void visit(SimpleNode node) {
        switch (node.getType()) {
            case Apostrophe:
                printer.print("&rsquo;");
                break;
            case Ellipsis:
                printer.print("&hellip;");
                break;
            case Emdash:
                printer.print("&mdash;");
                break;
            case Endash:
                printer.print("&ndash;");
                break;
            case HRule:
                printer.println().print("'''");
                break;
            case Linebreak:
                printer.print("\n");
                break;
            case Nbsp:
                printer.print("&nbsp;");
                break;
            default:
                throw new IllegalStateException();
        }
    }

    public void visit(StrongEmphSuperNode node) {
        if (node.isClosed()) {
            if (node.isStrong()) {
                printNode(node, "*");
            } else {
                printNode(node, "_");
            }
        } else {
            //sequence was not closed, treat open chars as ordinary chars
            printer.print(node.getChars());
            visitChildren(node);
        }
    }

    public void visit(StrikeNode node) {
        printTag(node, "del");
    }

    public void visit(TableBodyNode node) {
//        printIndentedTag(node, "tbody");
        visitChildren(node);
    }

    @Override
    public void visit(TableCaptionNode node) {
        printer.println().print("<caption>");
        visitChildren(node);
        printer.print("</caption>");
    }

    public void visit(TableCellNode node) {
//        String tag = inTableHeader ? "th" : "td";
        List<TableColumnNode> columns = currentTableNode.getColumns();
        TableColumnNode column = columns.get(Math.min(currentTableColumn, columns.size() - 1));

//        printer.println().print('<').print(tag);
        printer.print("|");
        column.accept(this);
        if (node.getColSpan() > 1) printer.print(" colspan=\"").print(Integer.toString(node.getColSpan())).print('"');
//        printer.print('>');
        visitChildren(node);
//        printer.print('<').print('/').print(tag).print('>');

        currentTableColumn += node.getColSpan();
    }

    public void visit(TableColumnNode node) {
        switch (node.getAlignment()) {
            case None:
                break;
            case Left:
                printer.print(" align=\"left\"");
                break;
            case Right:
                printer.print(" align=\"right\"");
                break;
            case Center:
                printer.print(" align=\"center\"");
                break;
            default:
                throw new IllegalStateException();
        }
    }

    public void visit(TableHeaderNode node) {
        inTableHeader = true;
//        printIndentedTag(node, "thead");

        visitChildren(node);

        inTableHeader = false;
    }

    public void visit(TableNode node) {
        currentTableNode = node;

        printer.print("|===");
        visitChildren(node);
        printer.println();
        printer.print("|===");


//        printNode(node, "|===");

//        printer.print("|===");
//        printer.println();
//        printIndentedTag(node, "table");
        currentTableNode = null;
    }

    public void visit(TableRowNode node) {
        currentTableColumn = 0;

        printer.println();



        visitChildren(node);
//        printIndentedTag(node, "tr");

        if(inTableHeader) {
            printer.println();
        }


    }

    public void visit(VerbatimNode node) {
        printer.print("[source]");
        printer.println();
        repeat('-', 4);
        printer.println();
        printer.printEncoded(node.getText());
//        printer.println();
        repeat('-', 4);
        printer.println();
    }

    public void visit(WikiLinkNode node) {
        printLink(linkRenderer.render(node));
    }

    public void visit(TextNode node) {
        if (abbreviations.isEmpty()) {
            printer.print(node.getText());
        } else {
            printWithAbbreviations(node.getText());
        }
    }

    public void visit(SpecialTextNode node) {
        printer.printEncoded(node.getText());
    }

    public void visit(SuperNode node) {
        visitChildren(node);
    }

    public void visit(Node node) {
        throw new RuntimeException("Don't know how to handle node " + node);
    }

    // helpers

    protected void visitChildren(AbstractNode node) {
        for (Node child : node.getChildren()) {
            child.accept(this);
        }
    }

//    @Deprecated
//    protected void printTag(TextNode node, String tag) {
//        printer.print('<').print(tag).print('>');
//        printer.printEncoded(node.getText());
//        printer.print('<').print('/').print(tag).print('>');
//    }

    protected void printNode(AbstractNode node, String token) {
        printer.print(token);
        visitChildren(node);
        printer.print(token);
    }


    @Deprecated
    protected void printTag(SuperNode node, String tag) {
        printer.print('<').print(tag).print('>');
        visitChildren(node);
        printer.print('<').print('/').print(tag).print('>');
    }

    @Deprecated
    protected void printIndentedTag(SuperNode node, String tag) {
        printer.println().print('<').print(tag).print('>').indent(+2);
        visitChildren(node);
        printer.indent(-2).println().print('<').print('/').print(tag).print('>');
    }

//    protected void visitChildrenIndented(SuperNode node) {
//        printer.println().indent(+2);
//        visitChildren(node);
//        printer.indent(-2).println();
//    }

    protected void printImageTag(LinkRenderer.Rendering rendering) {
        printer.print("image:");
        printer.print(rendering.href);
        for (LinkRenderer.Attribute attr : rendering.attributes) {
            printAttribute(attr.name, attr.value);
        }
        printer.print('[').print(rendering.text).print(']');
    }

    protected void printLink(LinkRenderer.Rendering rendering) {
        printer.print(rendering.href);
        for (LinkRenderer.Attribute attr : rendering.attributes) {
            printAttribute(attr.name, attr.value);
        }
        printer.print('[').print(rendering.text).print("]");
    }

    private void printAttribute(String name, String value) {
        printer.print(' ').print(name).print('=').print('"').print(value).print('"');
    }

    protected String printChildrenToString(SuperNode node) {
        Printer priorPrinter = printer;
        printer = new Printer();
        visitChildren(node);
        String result = printer.getString();
        printer = priorPrinter;
        return result;
    }

    protected String normalize(String string) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            switch (c) {
                case ' ':
                case '\n':
                case '\t':
                    continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    protected void printWithAbbreviations(String string) {
        Map<Integer, Map.Entry<String, String>> expansions = null;

        for (Map.Entry<String, String> entry : abbreviations.entrySet()) {
            // first check, whether we have a legal match
            String abbr = entry.getKey();

            int ix = 0;
            while (true) {
                int sx = string.indexOf(abbr, ix);
                if (sx == -1) break;

                // only allow whole word matches
                ix = sx + abbr.length();

                if (sx > 0 && Character.isLetterOrDigit(string.charAt(sx - 1))) continue;
                if (ix < string.length() && Character.isLetterOrDigit(string.charAt(ix))) {
                    continue;
                }

                // ok, legal match so save an expansions "task" for all matches
                if (expansions == null) {
                    expansions = new TreeMap<Integer, Map.Entry<String, String>>();
                }
                expansions.put(sx, entry);
            }
        }

        if (expansions != null) {
            int ix = 0;
            for (Map.Entry<Integer, Map.Entry<String, String>> entry : expansions.entrySet()) {
                int sx = entry.getKey();
                String abbr = entry.getValue().getKey();
                String expansion = entry.getValue().getValue();

                printer.printEncoded(string.substring(ix, sx));
                printer.print("<abbr");
                if (StringUtils.isNotEmpty(expansion)) {
                    printer.print(" title=\"");
                    printer.printEncoded(expansion);
                    printer.print('"');
                }
                printer.print('>');
                printer.printEncoded(abbr);
                printer.print("</abbr>");
                ix = sx + abbr.length();
            }
            printer.print(string.substring(ix));
        } else {
            printer.print(string);
        }
    }
}
