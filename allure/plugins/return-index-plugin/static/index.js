var url = window.location.href.split("/").slice(0,7).join("/");

class Layout extends allure.components.AppLayout {
    initialize() {
    }
}

if(window.parent.location.href.split("/").slice(0,-1).includes("dashboards")) {
    allure.api.addTab('return', {
        title: 'Return',
        icon: 'fa fa-undo',
        route: 'return',
        onEnter: (function () {
            return new Layout();
        })
    });

    window.onhashchange = function () {
        window.setTimeout(changeHref, 1000)
    };
    window.addEventListener("load", function () {
        window.setTimeout(changeHref, 1000)
    })
}

function changeHref() {
    var navs = document.getElementsByClassName("side-nav__item");
    navs[navs.length-3].getElementsByTagName("a")[0].href = url;
}
