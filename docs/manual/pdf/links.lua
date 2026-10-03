-- Turns the manual's links, written for GitHub, into links that work inside one PDF.
--
-- A chapter links to another chapter by its file, "05-plans.md", or to one of its headings,
-- "05-plans.md#only-some-operations". In the PDF every chapter is in the same document, so the
-- first goes to the chapter's own heading and the second to the heading it names. A link to the
-- rest of the documentation, "../report.md", goes to the same file on GitHub, at the commit the PDF
-- was built from, which docs/manual/pdf.sh passes as the metadata field "ref".

local repository = "https://github.com/isa-group/RESTest/blob/"
local manual = "docs/manual/"

local function file_name(path)
  return path:match("([^/]+)$")
end

-- "docs/manual/../report.md" -> "docs/report.md"
local function normalised(path)
  local parts = {}
  for part in path:gmatch("[^/]+") do
    if part == ".." then
      table.remove(parts)
    elseif part ~= "." then
      table.insert(parts, part)
    end
  end
  return table.concat(parts, "/")
end

function Pandoc(doc)
  local ref = doc.meta.ref and pandoc.utils.stringify(doc.meta.ref) or "master"

  -- Each chapter begins with its only first-level heading, and the chapters come in the order the
  -- files were given, so the n-th such heading belongs to the n-th file.
  local chapters = {}
  local files = PANDOC_STATE.input_files
  local seen = 0
  for _, block in ipairs(doc.blocks) do
    if block.t == "Header" and block.level == 1 then
      seen = seen + 1
      if files[seen] then
        chapters[file_name(files[seen])] = block.identifier
      end
    end
  end

  return doc:walk({
    Link = function(link)
      local target = link.target
      if target:match("^[%a][%w+.-]*:") or target:match("^#") then
        return nil
      end
      local file, anchor = target:match("^([^#]*)#?(.*)$")
      local chapter = chapters[file_name(file)]
      if chapter and not file:match("/") then
        link.target = "#" .. (anchor ~= "" and anchor or chapter)
      else
        link.target = repository .. ref .. "/" .. normalised(manual .. file)
            .. (anchor ~= "" and ("#" .. anchor) or "")
      end
      return link
    end,
  })
end
